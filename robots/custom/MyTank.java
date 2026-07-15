package custom;

import java.awt.Color;
import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

/**
 * MyTank - 1v1 AdvancedRobot tuned for unknown single opponents.
 *
 * It combines a tight radar lock, circular/linear predictive targeting, and a
 * wall-smoothed perpendicular orbit that reverses on enemy fire.  The movement
 * is intentionally a little irregular so simple linear/head-on guns have a hard
 * time collecting repeated hits.
 */
public class MyTank extends AdvancedRobot {
    private static final double WALL_MARGIN = 58.0;
    private static final double PREFERRED_DISTANCE = 410.0;

    private int moveDirection = 1;
    private double lastEnemyEnergy = 100.0;
    private double lastEnemyHeading = 0.0;
    private boolean haveEnemyHeading = false;
    private long lastScanTime = -1000;
    private long lastDirectionChangeTime = -1000;
    private int stationaryScans = 0;
    private int slowEnemyScans = 0;
    private int enemyFireCount = 0;
    private double enemyFirePowerAvg = 0.0;
    private int enemyFirePowerSamples = 0;
    private int wallEnemyScans = 0;
    private int straightEnemyScans = 0;
    private int crazyEnemyScans = 0;
    private int stopGoEnemyScans = 0;
    private double enemyVelocityAvg = 0.0;
    private double enemyTurnRateAvg = 0.0;
    private boolean haveEnemyAxis = false;
    private double enemyAxisHeading = 0.0;
    private double enemyAxisMin = 0.0;
    private double enemyAxisMax = 0.0;
    private int enemyAxisSamples = 0;

    // Lightweight virtual guns.  The latest opponent dodges enough that pure
    // circular prediction over-leads badly; keep rolling errors for several
    // aim styles and let the bot pick the one matching the current enemy.
    private static final int GUN_HEAD_ON = 0;
    private static final int GUN_LINEAR = 1;
    private static final int GUN_CIRCULAR = 2;
    private static final int GUN_AVERAGED = 3;
    private static final int GUN_GUESS_FACTOR = 4;
    private static final int GUN_DRIFT_HEAD_ON = 5;
    private static final int GUN_COUNT = 6;
    private static final int VIRTUAL_WAVES = 96;
    private final double[] virtualGunError = {55.0, 60.0, 60.0, 58.0, 70.0, 56.0};
    private final boolean[] virtualActive = new boolean[VIRTUAL_WAVES];
    private final long[] virtualTime = new long[VIRTUAL_WAVES];
    private final double[] virtualSourceX = new double[VIRTUAL_WAVES];
    private final double[] virtualSourceY = new double[VIRTUAL_WAVES];
    private final double[] virtualSpeed = new double[VIRTUAL_WAVES];
    private final double[][] virtualX = new double[GUN_COUNT][VIRTUAL_WAVES];
    private final double[][] virtualY = new double[GUN_COUNT][VIRTUAL_WAVES];
    private int virtualIndex = 0;
    private int virtualSamples = 0;
    private static final int GF_BINS = 31;
    private final double[] guessFactors = new double[GF_BINS];
    private final double[] virtualBearing = new double[VIRTUAL_WAVES];
    private final double[] virtualMaxEscape = new double[VIRTUAL_WAVES];
    private final double[] virtualLateralDirection = new double[VIRTUAL_WAVES];
    private double lastLateralDirection = 1.0;

    public void run() {
        setBodyColor(new Color(18, 24, 34));
        setGunColor(new Color(240, 190, 45));
        setRadarColor(new Color(80, 220, 255));
        setBulletColor(Color.WHITE);
        setScanColor(Color.CYAN);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
        setAhead(160);
        while (true) {
            // If radar lock is lost, sweep.  Keep issuing movement so we never
            // sit still during a long initial search or after a missed scan.
            if (getTime() - lastScanTime > 12) {
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
                if (Math.abs(getDistanceRemaining()) < 20) {
                    setAhead(140 * moveDirection);
                }
            }
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        lastScanTime = getTime();

        double absBearing = getHeadingRadians() + e.getBearingRadians();
        double enemyX = getX() + Math.sin(absBearing) * e.getDistance();
        double enemyY = getY() + Math.cos(absBearing) * e.getDistance();

        // Narrow radar lock with overshoot in the direction we need to turn.
        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 2.0);

        double scanTurnRate = haveEnemyHeading
                ? Utils.normalRelativeAngle(e.getHeadingRadians() - lastEnemyHeading)
                : 0.0;
        enemyVelocityAvg = 0.84 * enemyVelocityAvg + 0.16 * e.getVelocity();
        enemyTurnRateAvg = 0.84 * enemyTurnRateAvg + 0.16 * scanTurnRate;
        updateEnemyAxis(enemyX, enemyY, e.getHeadingRadians(), scanTurnRate);

        if (Math.abs(e.getVelocity()) < 0.05) {
            stationaryScans++;
        } else {
            stationaryScans = 0;
        }
        if (Math.abs(e.getVelocity()) <= 3.25) {
            slowEnemyScans++;
        } else {
            slowEnemyScans = 0;
        }
        // Terminator/CTBot-style opponents often alternate between full-speed
        // bursts and complete stops.  A consecutive slow counter misses these
        // frequent hard stops, so keep a small leaky score for stop/go motion;
        // it lets us keep the damped wall predictor instead of over-leading a
        // freshly stopped target that only looked straight a few ticks earlier.
        if (Math.abs(e.getVelocity()) < 0.15) {
            stopGoEnemyScans = Math.min(40, stopGoEnemyScans + 2);
        } else {
            stopGoEnemyScans = Math.max(0, stopGoEnemyScans - 1);
        }
        // Several logged opponents (including the current genetic bot) spend
        // long stretches pinned against a wall.  When a target is wall-bound it
        // has only one real escape direction, and our virtual-gun traces show
        // simple head-on shots beat circular/linear over-leading.  Track this
        // separately from "slow" because the bot can still burst at max speed
        // while sliding along the edge.
        if (enemyNearWall(enemyX, enemyY, 70.0)) {
            wallEnemyScans++;
        } else {
            wallEnemyScans = Math.max(0, wallEnemyScans - 2);
        }
        // The current antiwalls opponent spends many rounds on long straight
        // cardinal runs (often away from the actual walls).  Full linear
        // prediction is excellent there, but the virtual guns need several
        // bullet flights to learn it.  Confirm straight motion for a few scans
        // so we can cold-start the linear gun and close the orbit a bit.
        if (Math.abs(e.getVelocity()) > 0.55 && Math.abs(scanTurnRate) < 0.012) {
            straightEnemyScans++;
        } else {
            straightEnemyScans = Math.max(0, straightEnemyScans - 2);
        }
        // Robocode sample.Crazy-style bots run at high speed while constantly
        // turning.  Trace replay for the current opponent strongly favors a
        // circular gun; do not let the older active-wall/straight-run special
        // cases override that once this signature is established.
        if (Math.abs(e.getVelocity()) > 5.2 && Math.abs(scanTurnRate) > 0.035 && wallEnemyScans <= 10) {
            crazyEnemyScans++;
        } else {
            crazyEnemyScans = Math.max(0, crazyEnemyScans - 1);
        }

        updateVirtualGuns(enemyX, enemyY);

        double lateralVelocity = e.getVelocity() * Math.sin(e.getHeadingRadians() - absBearing);
        if (Math.abs(lateralVelocity) > 0.12) {
            lastLateralDirection = lateralVelocity >= 0 ? 1.0 : -1.0;
        }

        doMovement(e, absBearing);
        doGun(e, absBearing, enemyX, enemyY);

        lastEnemyEnergy = e.getEnergy();
        lastEnemyHeading = e.getHeadingRadians();
        haveEnemyHeading = true;
    }

    private void doMovement(ScannedRobotEvent e, double absBearing) {
        double enemyDrop = lastEnemyEnergy - e.getEnergy();
        // Enemy energy drops usually mean it fired, but at point-blank range a
        // robot collision/ram costs exactly about 0.6 energy.  Treating those
        // as bullets made close stationary-spawn fights reverse every tick and
        // could pin us in a Fire-style ram loop.  Ignore that ram signature.
        boolean likelyRamDrop = e.getDistance() < 90.0 && enemyDrop > 0.52 && enemyDrop < 0.68;
        if (enemyDrop > 0.09 && enemyDrop <= 3.01 && !likelyRamDrop) {      // likely enemy bullet
            enemyFireCount++;
            enemyFirePowerAvg = enemyFirePowerSamples == 0
                    ? enemyDrop
                    : 0.82 * enemyFirePowerAvg + 0.18 * enemyDrop;
            enemyFirePowerSamples++;
            reverseDirection();
        }

        // SittingDuck-style opponents in many logs never fire.  Once we are
        // confident a stationary target is harmless, cancel movement so we do
        // not donate wall damage while the gun farms max-power hits.  One
        // important exception: if the round spawned us almost touching a
        // stationary bot (sample.Fire in the current traces), stopping here can
        // pin both robots together in a ram loop until a draw.  Always open a
        // safe gap from close stationary targets before entering farm mode.
        if (stationaryScans > 5 && e.getDistance() < 260.0) {
            double away = absBearing + Math.PI;
            if (!insideBattlefield(projectX(getX(), away, 230.0), projectY(getY(), away, 230.0), WALL_MARGIN + 18.0)) {
                away = Math.atan2(getBattleFieldWidth() / 2.0 - getX(), getBattleFieldHeight() / 2.0 - getY());
            }
            setMaxVelocity(8.0);
            driveAlongAngle(away, 240.0);
            return;
        }
        if (stationaryScans > 10 && enemyFireCount == 0) {
            if (!insideBattlefield(getX(), getY(), WALL_MARGIN + 25.0)) {
                driveToward(getBattleFieldWidth() / 2.0, getBattleFieldHeight() / 2.0, 120.0);
                setMaxVelocity(6.0);
            } else {
                setTurnRightRadians(0.0);
                setAhead(0.0);
                setMaxVelocity(0.0);
            }
            return;
        }

        // Irregular reversals break simple linear targeting and prevent long
        // straight runs.  Guard reversals with a cooldown; flipping every scan
        // at very close/long range can leave us oscillating into a wall.
        if ((getTime() + 17) % 61 == 0 || e.getDistance() < 150 || e.getDistance() > 610) {
            if (getTime() - lastDirectionChangeTime > 14) {
                reverseDirection();
            }
        }

        // Orbit perpendicular, with a distance-control offset.  Far away we cut
        // inward; too close we open out.  wallSmooth then bends the path away
        // from the battlefield edges before we commit to it.
        double preferredDistance;
        if (crazyEnemyScans > 4) {
            preferredDistance = 305.0;
        } else if (weakFixedAxisOscillator()) {
            // Current Tarektank-style target is a one-dimensional 100px
            // oscillator with a weak fixed-heading gun.  Move closer than the
            // generic Ian/RegullarMonk conservation profile to shorten bullet
            // flight and make midpoint/axis shots land before it reverses.
            preferredDistance = 245.0;
        } else if (fixedHeadingStopGoEnemy()) {
            // Current OppsWantMeDead-style bot keeps an almost perfectly fixed
            // body heading while alternating stops/straight bursts and weak shots.
            // It scores very little, so stay closer and shorten head-on flights.
            preferredDistance = 305.0;
        } else if (fixedHeadingLineEnemy()) {
            // MyFirstKiller-style bots also hold one body heading, but may travel
            // over a longer line segment than the tighter fixedHeadingStopGoEnemy()
            // midpoint detector allows.  Stay in the short-flight exchange band.
            preferredDistance = 305.0;
        } else if (fastWallCruiser()) {
            preferredDistance = 305.0;
        } else if (activeStopGoShooter()) {
            // RegullarMonk-style bots stop/reverse constantly but fire repeated
            // weak bullets.  They are easiest to hit with fast head-on shots;
            // stay near its usual 320-350px exchange band instead of drifting
            // wide into long, low-damage self-depletion rounds.
            preferredDistance = 340.0;
        } else if (dangerousWallEnemy()) {
            preferredDistance = 335.0;
        } else if (straightEnemyScans > 16 && harmlessLowFireEnemy() && wallEnemyScans <= 4) {
            preferredDistance = 310.0;
        } else if (straightEnemyScans > 4 && harmlessLowFireEnemy()) {
            preferredDistance = 275.0;
        } else if (wallEnemyScans > 4) {
            preferredDistance = stopGoEnemyScans > 8 ? 285.0 : 305.0;
        } else if (activeStopGoEnemy()) {
            // MarkIV-style bots alternate long stops with short bursts and fire
            // mostly weak bullets.  Staying close shortens our bullet flight and
            // raises hit/kill speed; logs show plenty of spare survival energy.
            preferredDistance = 285.0;
        } else if (headOnGunIsBest()) {
            preferredDistance = 330.0;
        } else if (slowEnemyScans > 12) {
            preferredDistance = 285.0;
        } else {
            preferredDistance = PREFERRED_DISTANCE;
        }
        // Against the current GF-style opponent our gun struggles mostly due
        // to long bullet flight, while its own gun almost never connects.  Once
        // virtual guns report a hard-to-hit mover, tighten the orbit a bit to
        // shorten flight time and improve hit/kill speed without going to ram range.
        if (!dangerousWallEnemy() && !activeStopGoShooter()
                && virtualSamples > 28 && bestGunError() > 72.0 && stationaryScans <= 5 && slowEnemyScans <= 12) {
            preferredDistance = 355.0;
        }
        double distanceOffset = limit(-0.62, (e.getDistance() - preferredDistance) / 430.0, 0.55);
        double desired = absBearing + moveDirection * (Math.PI / 2.0 - distanceOffset);
        if (e.getDistance() < 118 && enemyFireCount == 0) {
            // Many simple bots only become dangerous at spawn/knife range.
            // Open the gap immediately instead of trying to orbit through a
            // point-blank power-3 shot or accidental ram.
            desired = absBearing + Math.PI;
        }
        desired = wallSmooth(desired, moveDirection);
        // If we are already in the danger band near an edge, prioritize getting
        // back into the field over maintaining a perfect orbit.
        if (!insideBattlefield(getX(), getY(), WALL_MARGIN + 18.0)) {
            desired = Math.atan2(getBattleFieldWidth() / 2.0 - getX(), getBattleFieldHeight() / 2.0 - getY());
        }

        double turn = Utils.normalRelativeAngle(desired - getHeadingRadians());
        double ahead = 150.0;
        // Robocode turns faster when we drive backward rather than demanding a
        // turn of more than 90 degrees.
        if (Math.cos(turn) < 0) {
            turn = Utils.normalRelativeAngle(turn + Math.PI);
            ahead = -ahead;
        }

        setTurnRightRadians(turn);
        setAhead(ahead);
        setMaxVelocity(Math.abs(turn) > Math.PI / 3 ? 5.5 : 8.0);
    }

    private void doGun(ScannedRobotEvent e, double absBearing, double enemyX, double enemyY) {
        double distance = e.getDistance();
        double turnRate = haveEnemyHeading ? Utils.normalRelativeAngle(e.getHeadingRadians() - lastEnemyHeading) : 0.0;
        double power;
        if (distance < 155) {
            power = 3.0;
        } else if (distance < 300) {
            power = 2.55;
        } else if (distance < 500) {
            power = 2.0;
        } else {
            power = 1.45;
        }
        // The recorded opponent (infinitylock) is a stationary radar/gun locker.
        // Once a target has sat still for several scans, use maximum power even
        // outside knife range: direct/circular prediction is exact and the faster
        // kill reduces exposure.  Moving opponents keep the conservative ladder.
        if (stationaryScans > 5 && getEnergy() > 12) {
            power = 3.0;
        } else if (wallEnemyScans > 4 && !dangerousWallEnemy() && getEnergy() > 14 && distance < 820) {
            // Wall-huggers have very limited escape room; use max-power
            // head-on/near-head-on shots to finish them before they can spend
            // energy on stray bullets (which lowers our available bullet score).
            power = 3.0;
        } else if (straightEnemyScans > 2 && harmlessLowFireEnemy() && getEnergy() > 14 && distance < 760) {
            // Straight runners are easy for the linear gun, even when they are
            // not close enough to the wall to trip wallEnemyScans.  Use max
            // power to shorten antiwalls-style rounds once the line is clear.
            power = 3.0;
        } else if (headOnGunIsBest() && getEnergy() > 18 && distance < 720) {
            // The current DeepThought opponent dodges/reverses enough that a
            // head-on gun wins the virtual-gun race.  Once detected, spend more
            // energy on heavier bullets: its own hit rate is tiny, and the
            // shorter rounds are worth the slightly slower bullet speed.
            power = Math.max(power, distance < 360 ? 3.0 : (distance < 520 ? 2.8 : 2.35));
        } else if ((slowEnemyScans > 8 || activeStopGoEnemy()) && getEnergy() > 12 && distance < 720) {
            // Slow and stop/go opponents give up enough predictable time that
            // heavier bullets trade a little travel time for much faster damage and
            // a larger bullet bonus.  Fast/unknown movers keep the safer ladder.
            power = 3.0;
        }
        boolean hardToHitMover = virtualSamples > 28 && bestGunError() > 72.0 && stationaryScans <= 5 && slowEnemyScans <= 12;
        // If all virtual guns are missing badly (as with wave-surfing GF-style
        // enemies), do not gamble the whole energy stack on repeated heavy
        // bullets.  Use tiny bullets at low energy: a hit gives more energy back
        // than it costs, while misses cannot self-kill us quickly.
        if (straightEnemyScans > 16 && harmlessLowFireEnemy() && wallEnemyScans <= 4) {
            // Sustained non-wall straight runners are harmless enough for heavy
            // bullets; the averaged gun still handles their stops/reverses.
            // Keep pressure high while the slightly wider orbit reduces rare
            // point-blank ram/leakage in long field-crossing runs.
            power = Math.max(power, distance < 620 ? 3.0 : 2.65);
        }
        if (fastWallCruiser() && getEnergy() > 14 && distance < 820) {
            power = Math.max(power, distance < 650 ? 3.0 : 2.55);
        }
        if (crazyEnemyScans > 4) {
            // High-speed continuous turners are easier to hit with faster,
            // moderate-power circular shots.  Previous max-power wall/straight
            // branches over-spent on sample.Crazy traces and made the lead error
            // much larger due to slow bullet flight.
            if (getEnergy() > 16) {
                power = Math.min(power, distance < 240 ? 2.50 : (distance < 460 ? 2.25 : 1.85));
            } else {
                power = Math.min(power, 1.25);
            }
        }
        if (weakFixedAxisOscillator()) {
            // Tarektank-like: fixed heading, short learned line segment, and
            // repeated weak power-1 shots.  The drift/axis gun is stable here,
            // so max-pressure bullets beat the older low-power conservation
            // profile and should cut the long 500+ tick farming rounds.
            if (getEnergy() > 28) {
                power = Math.max(power, distance < 620 ? 3.0 : 2.65);
            } else if (getEnergy() > 12) {
                power = Math.min(Math.max(power, 1.35), 2.0);
            } else {
                power = Math.min(power, 0.45);
            }
        } else if (fixedHeadingStopGoEnemy()) {
            // Ian's Tank / OppsWantMeDead-style fixed-heading stop/go shooters fire
            // steadily while barely turning their body.  Head-on is best, but logs
            // for Ian's Tank showed max-power spraying can self-deplete in long
            // games: lower power gives much faster bullets and far better geometric
            // hit rate against the 90px back-and-forth jiggle.  Keep enough pressure
            // while healthy, then fall to energy-positive pinpricks instead of dying
            // with repeated power-3 misses.
            if (getEnergy() > 42) {
                power = Math.min(power, distance < 430 ? 2.05 : 1.70);
            } else if (getEnergy() > 18) {
                power = Math.min(power, distance < 380 ? 1.15 : 0.85);
            } else if (getEnergy() > 8) {
                power = Math.min(power, 0.45);
            } else {
                power = Math.min(power, 0.15);
            }
        } else if (fixedHeadingLineEnemy()) {
            // Current MyFirstKiller traces: body heading never turns, it moves in
            // a one-dimensional forward/back line, and its power-1 gun is weak.
            // The old generic activeStopGoShooter cap (1.35-1.65) left us with
            // lots of spare energy and long rounds.  Use faster-than-max but still
            // high-damage bullets while healthy, then keep the proven low-energy
            // conservation behavior for any unexpectedly long game.
            if (getEnergy() > 48) {
                power = distance < 180 ? 3.0 : (distance < 360 ? 2.55 : (distance < 560 ? 2.25 : 1.85));
            } else if (getEnergy() > 20) {
                power = Math.min(power, distance < 430 ? 1.35 : 1.05);
            } else {
                power = Math.min(power, getEnergy() < 9 ? 0.15 : 0.45);
            }
        } else if (activeStopGoShooter()) {
            // RegullarMonk-like active stop/go shooters made us lose games by
            // self-depleting with repeated power-3 misses.  Head-on replay is
            // best and lower-power bullets are both faster and much safer.
            if (getEnergy() > 42) {
                power = Math.min(power, distance < 430 ? 1.65 : 1.35);
            } else if (getEnergy() > 18) {
                power = Math.min(power, 1.05);
            } else {
                power = Math.min(power, getEnergy() < 9 ? 0.15 : 0.45);
            }
        }
        if (dangerousWallEnemy() && crazyEnemyScans <= 4 && !activeStopGoShooter()) {
            // DroidPoet-style active wall runners are dangerous, but round-1
            // logs showed the previous wide/low-power survival tune gave away
            // too much bullet damage and even lost a couple of 10-round sets.
            // Keep pressure high while energy is healthy, then downshift before
            // we can self-deplete in very long perimeter chases.
            if (getEnergy() > 18 && distance < 760) {
                power = Math.max(power, distance < 520 ? 3.0 : 2.35);
            } else if (getEnergy() < 10) {
                power = Math.min(power, 0.45);
            } else {
                power = Math.min(Math.max(power, 1.65), 2.1);
            }
        }
        if (hardToHitMover) {
            if (getEnergy() < 12) {
                power = Math.min(power, 0.15);
            } else if (getEnergy() < 22 && bestGunError() > 82.0) {
                power = Math.min(power, 0.75);
            }
        }
        if (getEnergy() < 22 && distance > 260 && stationaryScans <= 5 && slowEnemyScans <= 12) {
            power = Math.min(power, 1.25);
        }
        if (getEnergy() < 9) {
            power = Math.min(power, hardToHitMover ? 0.15 : 0.55);
        }
        power = Math.min(power, Math.max(0.1, getEnergy() - 0.15));

        double bulletSpeed = 20.0 - 3.0 * power;

        double[][] candidates = new double[GUN_COUNT][2];
        candidates[GUN_HEAD_ON] = predictEnemy(enemyX, enemyY, e.getHeadingRadians(), e.getVelocity(), 0.0, bulletSpeed, GUN_HEAD_ON);
        candidates[GUN_LINEAR] = predictEnemy(enemyX, enemyY, e.getHeadingRadians(), e.getVelocity(), 0.0, bulletSpeed, GUN_LINEAR);
        candidates[GUN_CIRCULAR] = predictEnemy(enemyX, enemyY, e.getHeadingRadians(), e.getVelocity(), turnRate, bulletSpeed, GUN_CIRCULAR);
        candidates[GUN_AVERAGED] = predictEnemy(enemyX, enemyY, e.getHeadingRadians(), e.getVelocity(), turnRate, bulletSpeed, GUN_AVERAGED);
        candidates[GUN_GUESS_FACTOR] = predictGuessFactor(absBearing, distance, bulletSpeed);
        candidates[GUN_DRIFT_HEAD_ON] = predictEnemy(enemyX, enemyY, e.getHeadingRadians(), e.getVelocity(), 0.0, bulletSpeed, GUN_DRIFT_HEAD_ON);
        addVirtualWave(candidates, bulletSpeed, absBearing);

        int gun = chooseGun();
        if (stationaryScans > 5) {
            gun = GUN_HEAD_ON;
        } else if (crazyEnemyScans > 4) {
            gun = GUN_CIRCULAR;
        } else if (fixedHeadingStopGoEnemy()) {
            // Fixed-heading oscillators use the drift-head-on virtual gun slot for
            // learned-axis aiming: tight weak oscillators aim near the opposite
            // endpoint, while broader stop/go variants keep the safer midpoint.
            gun = GUN_DRIFT_HEAD_ON;
        } else if (fixedHeadingLineEnemy()) {
            // For longer fixed-heading line movers, a very small velocity drift
            // beats pure head-on in offline replay without over-leading stops.
            gun = GUN_DRIFT_HEAD_ON;
        } else if (activeStopGoShooter()) {
            // Current RegullarMonk traces: very frequent stops/reverses and
            // power-1 firing.  Offline shot replay favored head-on over linear,
            // circular, or averaged prediction; lower-power bullets handle the
            // target's small dodges without over-leading.
            gun = GUN_HEAD_ON;
        } else if (dangerousWallEnemy()) {
            // Against the active wall runner in the current logs, trace replay
            // favors the normal averaged stop/reversal predictor over head-on,
            // linear, circular, or the old wall-damped special case.
            gun = GUN_AVERAGED;
        } else if (wallEnemyScans > 4 && straightEnemyScans > 12
                && enemyFireCount <= 8
                && Math.abs(e.getVelocity()) > 4.5 && Math.abs(enemyVelocityAvg) > 3.6
                && Math.abs(turnRate) < 0.025 && crazyEnemyScans <= 4
                && !activeStopGoShooter() && !fixedHeadingStopGoEnemy()
                && (virtualSamples < 35 || virtualGunError[GUN_LINEAR] <= virtualGunError[GUN_AVERAGED] + 2.0)) {
            // daCruzer-style opponents spend most of the game on the perimeter and
            // fire a handful of weak shots, but unlike CTBot/Terminator their wall
            // movement includes long fast straight cruises.  The older low-fire
            // guard disabled the linear cold start after those shots, leaving the
            // damped stop/go wall gun to under-lead.  If the target is currently in
            // a genuine fast wall cruise, force linear unless virtual waves clearly
            // prefer the averaged gun.  Slow stop/go wall bots and DroidPoet-style
            // dangerous wall shooters are excluded above.
            gun = GUN_LINEAR;
        } else if (straightEnemyScans > 2 && harmlessLowFireEnemy() && wallEnemyScans <= 4) {
            // Short non-wall straight-looking snippets are often stop/reverse
            // noise (e.g. Tirolio).  The current Claptrap traces also show the
            // damped averaged gun slightly ahead of full linear at our actual
            // shot times, despite long straight runs.
            gun = GUN_AVERAGED;
        } else if (wallEnemyScans > 4 && straightEnemyScans > 12 && stopGoEnemyScans <= 8
                && Math.abs(e.getVelocity()) > 0.55 && Math.abs(turnRate) < 0.025
                && (Math.abs(enemyVelocityAvg) > 4.2 || Math.abs(e.getVelocity()) > 5.5)
                && (virtualSamples < 10 || virtualGunError[GUN_LINEAR] <= virtualGunError[GUN_AVERAGED] - 7.0)) {
            // Antiwalls/Claptrap-style bots can make long, clean, *fast* wall
            // runs where full linear prediction wins.  But the current it_simple
            // traces have many fast wall runs where the normal averaged predictor
            // is still a few pixels better than linear; only force linear for a
            // short cold start or after virtual waves show a clear linear margin.
            // Slow CTBot-style wall snippets keep the damped averaged predictor.
            gun = GUN_LINEAR;
        } else if (wallEnemyScans > 4 && virtualSamples < 18) {
            // Cold-start wall-bound targets with the damped wall predictor, but
            // do not force it forever.  The current antiwalls opponent slides
            // in long straight bursts along an edge, where the virtual guns
            // quickly learn that full linear/circular prediction is better
            // than the damped wall shot used for prior stop/reverse wall bots.
            gun = GUN_AVERAGED;
        } else if (activeStopGoEnemy()) {
            // Current Tibola/MarkIV traces show the damped averaged predictor is
            // better than full linear/circular for repeated stop-then-burst motion.
            gun = GUN_AVERAGED;
        } else if (virtualSamples < 14 && slowEnemyScans > 12) {
            gun = GUN_AVERAGED;
        }
        double predictedX = candidates[gun][0];
        double predictedY = candidates[gun][1];

        double aim = Math.atan2(predictedX - getX(), predictedY - getY());
        setTurnGunRightRadians(Utils.normalRelativeAngle(aim - getGunHeadingRadians()));

        // Fire when the gun is essentially on target.  The tolerance scales with
        // target width, so we still shoot promptly at close range.
        double tolerance = Math.atan2(28.0, distance);
        if (hardToHitMover) {
            // Do not spray wide-angle shots at surfers/random movers.  Waiting
            // a tick for a cleaner gun angle saves energy and raises hit rate.
            tolerance = Math.min(tolerance, Math.atan2(17.0, distance));
        }
        if (activeStopGoShooter()) {
            // RegullarMonk rounds are decided by long low-power exchanges; only
            // spend even the small conservation bullets when the head-on gun is
            // closely aligned.
            tolerance = Math.min(tolerance, Math.atan2(15.0, distance));
        } else if (fixedHeadingStopGoEnemy() || fixedHeadingLineEnemy()) {
            // Low-power/drift shots are cheap, but fixed-heading stop/go targets
            // are narrow in the direction that matters.  Tighten aim modestly,
            // without becoming stricter than the generic hard-to-hit tolerance.
            tolerance = Math.min(tolerance, Math.atan2(weakFixedAxisOscillator() ? 34.0 : 18.0, distance));
        }
        if (getGunHeat() == 0
                && Math.abs(getGunTurnRemainingRadians()) < tolerance && getEnergy() > 0.25) {
            setFire(power);
        }
    }

    private int chooseGun() {
        int best = GUN_HEAD_ON;
        if (slowEnemyScans > 12) {
            best = GUN_AVERAGED;
        }
        if (virtualSamples < 14) {
            return best;
        }
        for (int i = 0; i < GUN_COUNT; i++) {
            // The guess-factor gun is useful as a last resort, but it is noisy
            // early and hurt the recorded GF1 match when selected on a small
            // sample.  Require a longer history and a clear margin before it can
            // displace the simpler head-on/averaged guns.
            if (i == GUN_GUESS_FACTOR && (virtualSamples < 45 || virtualGunError[i] > virtualGunError[best] - 8.0)) {
                continue;
            }
            if (virtualGunError[i] < virtualGunError[best]) {
                best = i;
            }
        }
        return best;
    }

    private boolean harmlessLowFireEnemy() {
        // Claptrap/Tirolio/Antiwalls-style opponents may show one or two
        // energy drops from stray shots or wall/collision bookkeeping, but are
        // still effectively harmless.  Keep the aggressive straight-run farming
        // active until repeated firing proves otherwise; dangerousWallEnemy()
        // takes over after several shots for DroidPoet-like perimeter gunners.
        return enemyFireCount <= 2;
    }

    private boolean weakFixedAxisOscillator() {
        // alpian__tarektank in the current logs runs back and forth along one
        // unchanging heading over a compact ~100px segment and fires mostly
        // power-1 bullets.  Separate it from broader fixed-heading stop/go
        // shooters where previous max-power spraying caused self-depletion.
        return fixedHeadingStopGoEnemy()
                && enemyAxisSamples > 24
                && haveEnemyAxis
                && enemyAxisMax - enemyAxisMin > 42.0
                && enemyAxisMax - enemyAxisMin < 125.0
                && enemyFirePowerSamples > 0
                && enemyFirePowerAvg <= 1.35;
    }

    private boolean fastWallCruiser() {
        // daCruzer/Antiwalls-style perimeter cruisers: wall-bound, long straight
        // fast runs, and only a modest number of weak shots.  The first daCruzer
        // pass used a hard <=8-shot guard to keep DroidPoet's active wall gunner
        // out of this branch, but a few long daCruzer rounds exceed that count
        // after our misses prolong the chase; then dangerousWallEnemy() takes over
        // and wrongly forces the averaged gun.  Keep the conservative early guard,
        // but after virtual waves clearly show linear beating averaged, allow a
        // slightly higher fire count so the bot stays in the full-linear edge-slide
        // mode.  DroidPoet-style runners should not satisfy the virtual margin.
        boolean modestFire = enemyFireCount <= 8
                || (enemyFireCount <= 16 && virtualSamples > 20
                        && virtualGunError[GUN_LINEAR] + 12.0 < virtualGunError[GUN_AVERAGED]);
        return wallEnemyScans > 4
                && straightEnemyScans > 12
                && Math.abs(enemyVelocityAvg) > 3.6
                && modestFire
                && crazyEnemyScans <= 4;
    }

    private boolean activeStopGoShooter() {
        // RegullarMonk-style movement in the latest logs: half the time stopped,
        // small low-turn bursts, and many weak shots.  Treat it separately from
        // harmless stop/go bots and fast dangerous wall runners: conserve energy,
        // aim head-on, and stay wider.  The fixed-heading variant below is less
        // dangerous and can be pressured harder.
        return stopGoEnemyScans > 8
                && enemyFireCount > 3
                && !fixedHeadingStopGoEnemy()
                && !fastWallCruiser()
                && crazyEnemyScans <= 4
                && Math.abs(enemyVelocityAvg) < 3.8
                && Math.abs(enemyTurnRateAvg) < 0.035;
    }

    private boolean fixedHeadingStopGoEnemy() {
        // OppsWantMeDead-style signature from /logs/rounds/0: the opponent never
        // really turns its body (turn-rate EMA ~0), but repeatedly stops, moves
        // straight forward/back along that heading, and fires weak power-1 shots.
        // This is much easier to farm with head-on/max-pressure fire than the
        // broader activeStopGoShooter class, where max-power caused self-depletion.
        return stopGoEnemyScans > 8
                && enemyFireCount > 1
                && crazyEnemyScans <= 4
                && Math.abs(enemyVelocityAvg) < 3.8
                && Math.abs(enemyTurnRateAvg) < 0.004
                && (!haveEnemyAxis || enemyAxisSamples < 20 || enemyAxisMax - enemyAxisMin < 135.0);
    }

    private boolean fixedHeadingLineEnemy() {
        // MyFirstKiller-style signature in the current logs: identical body
        // heading for the whole round, repeated stops/straight bursts along one
        // axis, and a weak/simple firing pattern.  Unlike fixedHeadingStopGoEnemy
        // it can span well over a tight ~135px oscillator, so do not force the learned midpoint; use a
        // tiny drift projection and somewhat heavier bullets instead of the very
        // conservative activeStopGoShooter mode.
        return stopGoEnemyScans > 8
                && enemyFireCount > 1
                && crazyEnemyScans <= 4
                && Math.abs(enemyVelocityAvg) < 4.2
                && Math.abs(enemyTurnRateAvg) < 0.004
                && enemyAxisSamples > 18
                && (!haveEnemyAxis || enemyAxisMax - enemyAxisMin >= 135.0)
                && !fastWallCruiser();
    }

    private boolean activeStopGoEnemy() {
        // Tibola MarkIV-style movement: lots of stopped ticks and short bursts,
        // often with repeated weak shots.  Engage as soon as recent hard stops are
        // clear (stopGo > 8); waiting until >14 left some field shots after early
        // enemy fires using the less-damped predictor.  It is not DroidPoet's fast
        // active wall running or Crazy's continuous turn, and trace replay favors
        // a damped averaged gun plus a close orbit.  The virtual-error
        // guard prevents this from taking over long, hard-to-hit surfer battles.
        return stopGoEnemyScans > 8
                && Math.abs(enemyVelocityAvg) < 3.2
                && crazyEnemyScans <= 4
                && !dangerousWallEnemy()
                && (virtualSamples < 28 || bestGunError() < 70.0 || slowEnemyScans > 8);
    }

    private boolean dangerousWallEnemy() {
        // Current DroidPoet logs: a high-speed wall/perimeter runner that fires
        // often.  Do not wait for many virtual-wave samples before switching out
        // of the old "harmless wall target" max-power close-orbit mode.
        return wallEnemyScans > 4 && enemyFireCount > 3 && stopGoEnemyScans <= 12 && !fastWallCruiser();
    }

    private double bestGunError() {
        double best = virtualGunError[0];
        for (int i = 1; i < GUN_COUNT; i++) {
            best = Math.min(best, virtualGunError[i]);
        }
        return best;
    }

    private boolean headOnGunIsBest() {
        if (stationaryScans > 5 || wallEnemyScans > 4) {
            return true;
        }
        if (virtualSamples < 16) {
            return false;
        }
        double bestHeadFamily = Math.min(virtualGunError[GUN_HEAD_ON], virtualGunError[GUN_DRIFT_HEAD_ON]);
        double bestOther = Math.min(Math.min(Math.min(virtualGunError[GUN_LINEAR], virtualGunError[GUN_CIRCULAR]), virtualGunError[GUN_AVERAGED]), virtualGunError[GUN_GUESS_FACTOR]);
        return bestHeadFamily <= bestOther + 3.0;
    }

    private void addVirtualWave(double[][] candidates, double bulletSpeed, double absBearing) {
        int slot = virtualIndex++ % VIRTUAL_WAVES;
        virtualActive[slot] = true;
        virtualTime[slot] = getTime();
        virtualSourceX[slot] = getX();
        virtualSourceY[slot] = getY();
        virtualSpeed[slot] = bulletSpeed;
        virtualBearing[slot] = absBearing;
        virtualMaxEscape[slot] = Math.asin(8.0 / bulletSpeed);
        virtualLateralDirection[slot] = lastLateralDirection;
        for (int i = 0; i < GUN_COUNT; i++) {
            virtualX[i][slot] = candidates[i][0];
            virtualY[i][slot] = candidates[i][1];
        }
    }

    private void updateVirtualGuns(double enemyX, double enemyY) {
        for (int slot = 0; slot < VIRTUAL_WAVES; slot++) {
            if (!virtualActive[slot]) {
                continue;
            }
            long age = getTime() - virtualTime[slot];
            if (age <= 0) {
                continue;
            }
            if (age * virtualSpeed[slot] >= distance(virtualSourceX[slot], virtualSourceY[slot], enemyX, enemyY) - 18.0 || age > 90) {
                for (int gun = 0; gun < GUN_COUNT; gun++) {
                    double error = distance(virtualX[gun][slot], virtualY[gun][slot], enemyX, enemyY);
                    virtualGunError[gun] = 0.88 * virtualGunError[gun] + 0.12 * error;
                }
                double actualBearing = Math.atan2(enemyX - virtualSourceX[slot], enemyY - virtualSourceY[slot]);
                double offset = Utils.normalRelativeAngle(actualBearing - virtualBearing[slot]);
                double gf = limit(-1.0, offset / virtualMaxEscape[slot] * virtualLateralDirection[slot], 1.0);
                int bin = (int) Math.round((GF_BINS - 1) / 2.0 * (gf + 1.0));
                for (int i = 0; i < GF_BINS; i++) {
                    guessFactors[i] *= 0.997;
                }
                guessFactors[bin] += 1.0;
                virtualActive[slot] = false;
                virtualSamples++;
            }
        }
    }

    private double[] predictGuessFactor(double absBearing, double distance, double bulletSpeed) {
        int bestBin = GF_BINS / 2;
        double bestScore = guessFactors[bestBin];
        for (int i = 0; i < GF_BINS; i++) {
            double centerBias = 0.018 * (GF_BINS / 2 - Math.abs(i - GF_BINS / 2));
            if (guessFactors[i] + centerBias > bestScore) {
                bestScore = guessFactors[i] + centerBias;
                bestBin = i;
            }
        }
        double gf = (bestBin - (GF_BINS - 1) / 2.0) / ((GF_BINS - 1) / 2.0);
        double aim = absBearing + lastLateralDirection * gf * Math.asin(8.0 / bulletSpeed);
        return new double[] {getX() + Math.sin(aim) * distance, getY() + Math.cos(aim) * distance};
    }

    private double[] predictEnemy(double enemyX, double enemyY, double heading, double velocity,
            double turnRate, double bulletSpeed, int gunType) {
        if (gunType == GUN_HEAD_ON) {
            return new double[] {enemyX, enemyY};
        }
        if (gunType == GUN_DRIFT_HEAD_ON) {
            if (weakFixedAxisOscillator() && enemyAxisSamples > 18) {
                return predictAxisOppositeEndpoint(enemyX, enemyY);
            }
            if (fixedHeadingStopGoEnemy() && enemyAxisSamples > 18) {
                return predictAxisMidpoint(enemyX, enemyY);
            }
            // Generic fallback: almost-head-on with a tiny velocity drift.  For
            // longer fixed-heading line movers, offline replay favored about 10%
            // of the current forward/back velocity; keep other enemies at the old
            // 5% drift to avoid over-leading stop/reverse bots.
            double driftScale = fixedHeadingLineEnemy() ? 0.10 : 0.05;
            double drift = limit(-0.80, driftScale * velocity, 0.80);
            return projectClamped(enemyX, enemyY, heading, drift, bulletSpeed, 70);
        }
        if (gunType == GUN_AVERAGED && !dangerousWallEnemy()
                && (wallEnemyScans > 4 || (stopGoEnemyScans > 8 && (harmlessLowFireEnemy() || activeStopGoEnemy())))
                && !(stopGoEnemyScans <= 8 && straightEnemyScans > 12 && harmlessLowFireEnemy()
                        && (Math.abs(enemyVelocityAvg) > 3.5 || Math.abs(velocity) > 5.0))) {
            // A harmless wall-bound or recent stop/go bot often alternates between
            // max-speed bursts and hard stops/reverses.  Damping avoids over-leading
            // those weak opponents.  The current Terminator traces show this helps
            // even during brief field excursions after hard stops, not just while
            // the target is inside the wall margin.  However it_simple/Antiwalls
            // style clean edge runs can need less damping; the exception above and
            // the linear virtual-gun override still let fast straight low-fire runs
            // use fuller prediction when there have not been recent stops.
            velocity = limit(-2.2, 0.25 * velocity + 0.35 * enemyVelocityAvg, 2.2);
            turnRate = 0.0;
        } else if (gunType == GUN_AVERAGED) {
            // Good against stop-and-go and random-reversal bots: do not trust a
            // single-tick burst or stop to continue for the whole bullet flight.
            velocity = limit(-3.5, 0.45 * velocity + 0.55 * enemyVelocityAvg, 3.5);
            turnRate = limit(-0.09, 0.35 * turnRate + 0.65 * enemyTurnRateAvg, 0.09);
        } else if (gunType == GUN_LINEAR) {
            turnRate = 0.0;
        }

        double predictedX = enemyX;
        double predictedY = enemyY;
        double predictedHeading = heading;
        double time = 0.0;
        while ((++time) * bulletSpeed < distance(getX(), getY(), predictedX, predictedY) && time < 85) {
            if (Math.abs(turnRate) > 0.0005) {
                predictedHeading += turnRate;
            }
            predictedX += Math.sin(predictedHeading) * velocity;
            predictedY += Math.cos(predictedHeading) * velocity;
            if (!insideBattlefield(predictedX, predictedY, 18.0)) {
                predictedX = limit(18.0, predictedX, getBattleFieldWidth() - 18.0);
                predictedY = limit(18.0, predictedY, getBattleFieldHeight() - 18.0);
                break;
            }
        }
        return new double[] {predictedX, predictedY};
    }


    private void updateEnemyAxis(double enemyX, double enemyY, double heading, double turnRate) {
        // Fixed-heading stop/go opponents (current Ian's Tank) oscillate on a
        // one-dimensional line.  Aiming at the learned midpoint of that segment is
        // far more stable than projecting the current burst velocity.
        if (!haveEnemyAxis || Math.abs(Utils.normalRelativeAngle(heading - enemyAxisHeading)) > 0.06
                || Math.abs(turnRate) > 0.04) {
            haveEnemyAxis = true;
            enemyAxisHeading = heading;
            double axis = enemyX * Math.sin(enemyAxisHeading) + enemyY * Math.cos(enemyAxisHeading);
            enemyAxisMin = axis;
            enemyAxisMax = axis;
            enemyAxisSamples = 1;
            return;
        }
        double axis = enemyX * Math.sin(enemyAxisHeading) + enemyY * Math.cos(enemyAxisHeading);
        enemyAxisMin = Math.min(enemyAxisMin, axis);
        enemyAxisMax = Math.max(enemyAxisMax, axis);
        enemyAxisSamples++;
    }


    private double[] predictAxisOppositeEndpoint(double enemyX, double enemyY) {
        // Tarektank-style fixed-axis oscillators tend to reverse between the two
        // learned endpoints during our bullet flight.  Aiming at the midpoint was
        // safe, but trace replay for the current opponent shows the opposite end
        // of the compact segment is hit far more often.  Pull the aim point a
        // little inward from the exact endpoint so minor axis-learning noise or
        // robot-width clipping does not overshoot.
        double span = enemyAxisMax - enemyAxisMin;
        double mid = (enemyAxisMin + enemyAxisMax) / 2.0;
        double ux = Math.sin(enemyAxisHeading);
        double uy = Math.cos(enemyAxisHeading);
        double px = -Math.cos(enemyAxisHeading);
        double py = Math.sin(enemyAxisHeading);
        double currentAxis = enemyX * ux + enemyY * uy;
        double inset = limit(8.0, 0.15 * span, 16.0);
        double targetAxis = currentAxis > mid ? enemyAxisMin + inset : enemyAxisMax - inset;
        double perp = enemyX * px + enemyY * py;
        double predictedX = ux * targetAxis + px * perp;
        double predictedY = uy * targetAxis + py * perp;
        return new double[] {
                limit(18.0, predictedX, getBattleFieldWidth() - 18.0),
                limit(18.0, predictedY, getBattleFieldHeight() - 18.0)};
    }

    private double[] predictAxisMidpoint(double enemyX, double enemyY) {
        double mid = (enemyAxisMin + enemyAxisMax) / 2.0;
        double ux = Math.sin(enemyAxisHeading);
        double uy = Math.cos(enemyAxisHeading);
        double px = -Math.cos(enemyAxisHeading);
        double py = Math.sin(enemyAxisHeading);
        double perp = enemyX * px + enemyY * py;
        double predictedX = ux * mid + px * perp;
        double predictedY = uy * mid + py * perp;
        return new double[] {
                limit(18.0, predictedX, getBattleFieldWidth() - 18.0),
                limit(18.0, predictedY, getBattleFieldHeight() - 18.0)};
    }

    public void onHitByBullet(HitByBulletEvent e) {
        reverseDirection();
        setMaxVelocity(8.0);
        // e.getBearingRadians() is relative to our body heading.  To dodge the
        // bullet line, turn to a perpendicular bearing; driving backward after
        // a reversal gives the opposite perpendicular when that is faster.
        setTurnRightRadians(Utils.normalRelativeAngle(e.getBearingRadians() + Math.PI / 2.0));
        setAhead(170.0 * moveDirection);
    }

    public void onHitWall(HitWallEvent e) {
        reverseDirection();
        setMaxVelocity(8.0);
        driveToward(getBattleFieldWidth() / 2.0, getBattleFieldHeight() / 2.0, 170.0);
    }

    public void onHitRobot(HitRobotEvent e) {
        reverseDirection();
        setMaxVelocity(8.0);
        double robotBearing = getHeadingRadians() + e.getBearingRadians();
        double gunTurn = Utils.normalRelativeAngle(robotBearing - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);
        // Turn and drive directly away from the collision instead of just
        // backing up along our current heading.  The old response could be
        // overwritten by the stationary-target stop branch and leave us pinned
        // against close-spawn stationary shooters, causing needless ram loops
        // and the occasional draw.
        double escape = robotBearing + Math.PI;
        if (!insideBattlefield(projectX(getX(), escape, 150.0), projectY(getY(), escape, 150.0), WALL_MARGIN)) {
            escape = Math.atan2(getBattleFieldWidth() / 2.0 - getX(), getBattleFieldHeight() / 2.0 - getY());
        }
        driveAlongAngle(escape, 170.0);
        if (getGunHeat() == 0 && getEnergy() > 3) {
            setFire(3.0);
        }
    }

    public void onBulletHit(BulletHitEvent e) {
        // Keep the enemy energy estimate sane when our bullet lands between scans.
        lastEnemyEnergy = Math.max(0.0, e.getEnergy());
    }

    private double wallSmooth(double angle, int orientation) {
        double smoothed = angle;
        int tries = 0;
        while (!insideBattlefield(projectX(getX(), smoothed, 190.0), projectY(getY(), smoothed, 190.0), WALL_MARGIN)
                && tries++ < 28) {
            smoothed += orientation * 0.075;
        }
        if (insideBattlefield(projectX(getX(), smoothed, 190.0), projectY(getY(), smoothed, 190.0), WALL_MARGIN)) {
            return smoothed;
        }

        // If the preferred smoothing direction fails (common when spawned in a
        // corner), try the other way before falling back to the center escape.
        smoothed = angle;
        tries = 0;
        while (!insideBattlefield(projectX(getX(), smoothed, 190.0), projectY(getY(), smoothed, 190.0), WALL_MARGIN)
                && tries++ < 28) {
            smoothed -= orientation * 0.075;
        }
        if (insideBattlefield(projectX(getX(), smoothed, 190.0), projectY(getY(), smoothed, 190.0), WALL_MARGIN)) {
            return smoothed;
        }
        return Math.atan2(getBattleFieldWidth() / 2.0 - getX(), getBattleFieldHeight() / 2.0 - getY());
    }

    private void reverseDirection() {
        moveDirection = -moveDirection;
        lastDirectionChangeTime = getTime();
    }


    private void driveAlongAngle(double angle, double distance) {
        double turn = Utils.normalRelativeAngle(angle - getHeadingRadians());
        double ahead = distance;
        if (Math.cos(turn) < 0) {
            turn = Utils.normalRelativeAngle(turn + Math.PI);
            ahead = -distance;
        }
        setTurnRightRadians(turn);
        setAhead(ahead);
    }

    private void driveToward(double x, double y, double distance) {
        double angle = Math.atan2(x - getX(), y - getY());
        double turn = Utils.normalRelativeAngle(angle - getHeadingRadians());
        double ahead = distance;
        if (Math.cos(turn) < 0) {
            turn = Utils.normalRelativeAngle(turn + Math.PI);
            ahead = -distance;
        }
        setTurnRightRadians(turn);
        setAhead(ahead);
    }

    private double[] projectClamped(double enemyX, double enemyY, double heading, double velocity, double bulletSpeed, int maxTicks) {
        double predictedX = enemyX;
        double predictedY = enemyY;
        double time = 0.0;
        while ((++time) * bulletSpeed < distance(getX(), getY(), predictedX, predictedY) && time < maxTicks) {
            predictedX += Math.sin(heading) * velocity;
            predictedY += Math.cos(heading) * velocity;
            if (!insideBattlefield(predictedX, predictedY, 18.0)) {
                predictedX = limit(18.0, predictedX, getBattleFieldWidth() - 18.0);
                predictedY = limit(18.0, predictedY, getBattleFieldHeight() - 18.0);
                break;
            }
        }
        return new double[] {predictedX, predictedY};
    }

    private boolean insideBattlefield(double x, double y, double margin) {
        return x > margin && x < getBattleFieldWidth() - margin
                && y > margin && y < getBattleFieldHeight() - margin;
    }

    private boolean enemyNearWall(double x, double y, double margin) {
        return x < margin || x > getBattleFieldWidth() - margin
                || y < margin || y > getBattleFieldHeight() - margin;
    }

    private static double projectX(double x, double angle, double length) {
        return x + Math.sin(angle) * length;
    }

    private static double projectY(double y, double angle, double length) {
        return y + Math.cos(angle) * length;
    }

    private static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }
}
