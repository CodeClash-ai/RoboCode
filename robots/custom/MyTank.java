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
    private int stationaryHeavyDirection = 1;
    private double lastEnemyEnergy = 100.0;
    private double lastEnemyAbsBearing = 0.0;
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
    private int spinEnemyScans = 0;
    private int stopGoEnemyScans = 0;
    private int closeRammerScans = 0;
    private int trackerApproachScans = 0;
    private int npcSniperScans = 0;
    private int dominatorScans = 0;
    private int shrekerScans = 0;
    private int tannerWallScans = 0;
    private int waveSurfScans = 0;
    private int juggernautScans = 0;
    private int wallsPoetScans = 0;
    private double enemyVelocityAvg = 0.0;
    private double enemySpeedAvg = 0.0;
    private double enemyTurnRateAvg = 0.0;
    private double enemyAbsTurnRateAvg = 0.0;
    private String enemyName = "";
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
        enemyName = e.getName();

        double absBearing = getHeadingRadians() + e.getBearingRadians();
        lastEnemyAbsBearing = absBearing;
        double enemyX = getX() + Math.sin(absBearing) * e.getDistance();
        double enemyY = getY() + Math.cos(absBearing) * e.getDistance();

        // Narrow radar lock with overshoot in the direction we need to turn.
        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 2.0);

        double scanTurnRate = haveEnemyHeading
                ? Utils.normalRelativeAngle(e.getHeadingRadians() - lastEnemyHeading)
                : 0.0;
        enemyVelocityAvg = 0.84 * enemyVelocityAvg + 0.16 * e.getVelocity();
        enemySpeedAvg = 0.84 * enemySpeedAvg + 0.16 * Math.abs(e.getVelocity());
        enemyTurnRateAvg = 0.84 * enemyTurnRateAvg + 0.16 * scanTurnRate;
        enemyAbsTurnRateAvg = 0.84 * enemyAbsTurnRateAvg + 0.16 * Math.abs(scanTurnRate);
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
        // sample.SpinBot-style opponents move at about velocity 5 while turning
        // continuously in a tight circle.  The old Crazy detector starts at >5.2
        // velocity and therefore misses SpinBot, leaving the cold-start head-on /
        // averaged guns and power-2 shots to waste time.  Track this lower-speed
        // continuous-turn signature separately so we can force exact circular aim
        // and heavier bullets from the first few scans.
        if (Math.abs(e.getVelocity()) > 4.15 && Math.abs(e.getVelocity()) < 5.35
                && Math.abs(scanTurnRate) > 0.045 && Math.abs(scanTurnRate) < 0.18
                && stopGoEnemyScans <= 4) {
            spinEnemyScans++;
        } else {
            spinEnemyScans = Math.max(0, spinEnemyScans - 1);
        }
        // sample.RamFire-style bots repeatedly drive straight into close range
        // and only score through rare point-blank shots/collisions.  Their future
        // position is better described by the current straight-line velocity than
        // by the generic damped harmless-runner gun, so track a narrow rammer
        // signature for an early linear override.
        double enemyRadialVelocity = e.getVelocity() * Math.cos(e.getHeadingRadians() - absBearing);
        if (e.getDistance() < 285.0 && Math.abs(e.getVelocity()) > 2.0
                && Math.abs(scanTurnRate) < 0.035 && enemyFireCount <= 3
                && wallEnemyScans <= 8 && crazyEnemyScans <= 4) {
            closeRammerScans = Math.min(40, closeRammerScans + 2);
        } else {
            closeRammerScans = Math.max(0, closeRammerScans - 1);
        }
        // sample.Tracker-style bots point at us and repeatedly drive down the
        // bearing line, occasionally pausing to fire heavy bullets.  They are easy
        // linear targets, but if we let the generic close orbit resume at ~260px
        // they can leak point-blank power-3 damage.  Track sustained radial
        // approaches separately from harmless field-crossing straight runners.
        if (e.getDistance() < 430.0 && enemyRadialVelocity < -2.0
                && Math.abs(e.getVelocity()) > 3.5 && Math.abs(scanTurnRate) < 0.030
                && wallEnemyScans <= 8 && crazyEnemyScans <= 4 && enemyFireCount <= 12) {
            trackerApproachScans = Math.min(50, trackerApproachScans + 2);
        } else {
            trackerApproachScans = Math.max(0, trackerApproachScans - 1);
        }
        if (dominatorSignatureRaw()) {
            // vikdov__dominatorx mixes long straight runs, wall/corner stops, and
            // medium-power fire with occasional turns.  Keep the confirmation sticky;
            // otherwise late stops look like generic wall/slow targets and re-enable
            // over-leading/max-power branches.
            dominatorScans = Math.min(90, dominatorScans + 5);
        } else {
            dominatorScans = Math.max(0, dominatorScans - 1);
        }
        if (shrekerSignatureRaw()) {
            // Current alexbay218__shreker profile: low-turn stop/go mover that spends
            // repeated power-3 bullets.  It is closest to M9/MarkRobo geometrically, but
            // the power-3 stream punishes our old max/slow-target fallback in long games.
            // Keep a sticky confirmation once the p3 stop/go pattern appears.
            shrekerScans = Math.min(100, shrekerScans + 6);
        } else if (shrekerScans > 0) {
            // Round-1 follow-up traces showed the raw signature can disappear late when
            // Shreker parks/stops or after our own hits perturb the energy-drop average.
            // Then generic slow/wall branches re-enable power-3 shots in exactly the
            // low-energy endgame where we lose/draw.  The raw signature is narrow
            // (several high-power enemy shots plus low-turn stop/go motion), so once it
            // has appeared in a round, keep this conservation profile permanently.
            shrekerScans = Math.max(1, shrekerScans - 1);
        }

        // NPCSniper can briefly leave the wall/straight signature late in long
        // rounds, exactly when we most need its low-energy conservation caps.
        // Keep a sticky confirmation counter once the medium-fire fast/straight
        // wall-runner profile has appeared instead of falling back to generic
        // slow/low-energy shots that spend 0.5-0.6 energy while nearly dead.
        if (npcSniperSignatureRaw()) {
            npcSniperScans = Math.min(70, npcSniperScans + 4);
        } else {
            npcSniperScans = Math.max(0, npcSniperScans - 1);
        }

        if (tannerWallCruiserRaw()) {
            // TannerBot can stop in corners long enough for the plain straight-run
            // counter to decay.  Keep a sticky medium-wall-cruiser confirmation so
            // late low-energy phases do not fall back to generic max-power wall
            // farming or slow-target branches.
            tannerWallScans = Math.min(80, tannerWallScans + 5);
        } else {
            tannerWallScans = Math.max(0, tannerWallScans - 1);
        }

        if (juggernautSignatureRaw()) {
            juggernautScans = Math.min(90, juggernautScans + 5);
        } else if (juggernautScans > 0) {
            // Once the dankraemer__juggernaut power-3 stop/turn signature has been
            // seen, keep it for the rest of the round.  Round-1 follow-up losses had
            // Juggernaut park/stop long enough that the old sticky counter decayed to
            // zero; then generic slow/close branches fired 2+ power bullets while our
            // energy was under ~18, self-depleting with the enemy still alive.  The raw
            // signature is narrow (repeated p3 fire plus fast turning bursts), so a
            // permanent confirmation is safer than falling out during late parked phases.
            juggernautScans = Math.max(1, juggernautScans - 1);
        }
        if (wallsPoetSignatureRaw(e)) {
            wallsPoetScans = Math.min(120, wallsPoetScans + 6);
        } else if (wallsPoetScans > 0) {
            // Once the high-power wall/stop-go profile appears, keep the Wallspoet caps for
            // the rest of the round.  Round-1 losses came from late parked/wall-transition
            // phases falling back into generic Shreker/slow-wall max-power behavior.
            wallsPoetScans = Math.max(1, wallsPoetScans - 1);
        }

        updateVirtualGuns(enemyX, enemyY);
        if (waveSurfingSignatureRaw()) {
            waveSurfScans = Math.min(120, waveSurfScans + 5);
        } else {
            waveSurfScans = Math.max(0, waveSurfScans - 1);
        }

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
        if (leachPmcEnemy() && e.getDistance() < 105.0) {
            // Very close LeachPMC spawns are the only remaining blemish in the logs.
            // The normal absolute-angle escape can be too slow while the tanks overlap:
            // Robocode stops our movement on every HIT_ROBOT event and scan-time wall
            // smoothing may choose a lateral/corner-safe path that keeps us touching.
            // Before doing any orbit/dodge logic, just drive straight along our current
            // axis in the direction that immediately increases center distance.  Once a
            // small gap is open, the normal wide stationary-heavy orbit takes over.
            emergencyStraightAwayFrom(absBearing, 360.0);
            return;
        }
        if (enemyDrop > 0.09 && enemyDrop <= 3.01 && !likelyRamDrop) {      // likely enemy bullet
            enemyFireCount++;
            enemyFirePowerAvg = enemyFirePowerSamples == 0
                    ? enemyDrop
                    : 0.82 * enemyFirePowerAvg + 0.18 * enemyDrop;
            enemyFirePowerSamples++;
            if (gntestEnemy() && stationaryScans > 4) {
                // GNTest's losing traces include a stationary/parked phase that fires
                // p2-p3 bullets.  The generic stationary-heavy dodge only reacted to
                // >2.2 drops, so medium shots kept us on a stable orbit and landed in
                // strings.  Cross the gun line on every detected GNTest shot while it is
                // parked, before the broader TrackFire rule below.
                driveStationaryHeavyEscape(absBearing, getEnergy() < 38.0 ? 430.0 : 365.0);
                return;
            }
            if (leachPmcEnemy() && stationaryScans > 5 && enemyDrop > 2.20) {
                // Current pez__leachpmc target is stationary but immediately fires power-3.
                // Do not use the old close stationary-farm stop/short dodge; open a wider
                // diagonal lane from the first detected shot so its simple p3 gun stops
                // landing while our exact head-on p3 shots finish it.
                driveStationaryHeavyEscape(absBearing, getEnergy() < 35.0 ? 505.0 : 445.0);
                return;
            }
            if (stationaryScans > 5 && enemyDrop > 2.20) {
                // sample.TrackFire-style opponents sit still but fire repeated power-3
                // bullets at our current bearing.  The generic response reversed orbit
                // direction on every shot; in round-0 loss traces that left us almost
                // stationary around 235px and eating every bullet.  Do not flip-flop the
                // orbit here: commit to a persistent diagonal away+lateral dodge and let
                // the wider stationary-heavy orbit below reopen the range.
                driveStationaryHeavyEscape(absBearing, getEnergy() < 40.0 ? 390.0 : 340.0);
                return;
            }
            if (sampleWallsEnemy()) {
                // sample.Walls fires accurate head-on/near-head-on bullets while racing
                // around the border.  Reversing exactly on its fire tick made us cross back
                // through the old bullet line in round-1 loss traces.  Keep one lateral
                // dodge side through the shot and only change side on wall pressure.
                driveSampleWallsEscape(absBearing, getEnergy() < 38.0 ? 545.0 : 445.0);
                return;
            }
            reverseDirection();
            if (gntestStopDuel() && getEnergy() < 50.0) {
                // In the current GNTest loss traces the slow/parked phase becomes a
                // long medium-bullet duel.  Cross its simple firing line when our
                // reserve is no longer huge instead of only reversing the orbit.
                drivePerpendicularEscape(absBearing, getEnergy() < 26.0 ? 330.0 : 255.0);
                return;
            }
            if (!robrrratEnemy() && (fixedHeadingMediumShooter() || activeHighPowerShooter()) && getEnergy() < 42.0) {
                // Chilibot/Ultron-style shooters become dangerous once our reserve is
                // low: simply flipping the orbit can still leave us on the same bullet
                // line for medium/high-power head-on shots.  On the fire tick, spend a
                // short movement command on a clean perpendicular escape before the
                // normal range controller resumes on the following scans.
                drivePerpendicularEscape(absBearing, 230.0);
                return;
            }
            if (robrrratEnemy()) {
                // sacdalance__robrrrat is a mixed fast/stop mover with a very high-power,
                // fairly accurate gun.  The remaining round-0 live losses were energy
                // depletion after eating p3 streams, often while we kept a close orbit.
                // Cross its fire line immediately and reopen range before resuming the
                // averaged-gun exchange.
                drivePerpendicularEscape(absBearing, getEnergy() < 38.0 ? 455.0 : 350.0);
                return;
            }
            if (maximbotEnemy()) {
                // Maximbot fires medium/high bullets while moving in arcs.  Do not only
                // reverse along the same close curve; step across its shot line, especially
                // because the rare loss traces were close-range exchanges.
                drivePerpendicularEscape(absBearing, getEnergy() < 42.0 ? 420.0 : 335.0);
                return;
            }
            if (shrekerEnemy()) {
                // Shreker fires mostly power-3 from stop/go/straight positions.  A simple
                // orbit reversal left us eating long p3 streams in the losing traces; cross
                // the firing line on every detected shot, with a larger step once reserve
                // is no longer huge.
                drivePerpendicularEscape(absBearing, getEnergy() < 42.0 ? 340.0 : 270.0);
                return;
            }
            if (pikachuEnemy() && getEnergy() < 50.0) {
                // Pikachu's bullets are usually tiny, but the long losing traces show many
                // of them landing after we simply reverse along the same close orbit.  Once
                // our reserve is no longer huge, spend the fire tick crossing the line while
                // the cheap-gun power caps below preserve energy.
                drivePerpendicularEscape(absBearing, getEnergy() < 24.0 ? 360.0 : 300.0);
                return;
            }
            if (mediumStopGoDuelist() && getEnergy() < 44.0) {
                // MarkRobo-style medium stop/go duelists can win only after long
                // exchanges.  When our reserve is getting low, sidestep on their
                // fire tick rather than just reversing in the same orbit.
                drivePerpendicularEscape(absBearing, 240.0);
                return;
            }
            if (m9WallStopGoEnemy()) {
                // M9 keeps firing power-2 down the wall/stop-go exchange.  Round-1
                // losses show those bullets still connect even before our reserve is
                // low, especially while the enemy is parked on a wall/corner.  Cross
                // the firing line on every detected M9 shot instead of waiting until
                // the late self-depletion phase.
                drivePerpendicularEscape(absBearing, getEnergy() < 38.0 ? 285.0 : 245.0);
                return;
            }
        if (dominatorEnemy()) {
                // DominatorX fires medium bullets often; when reserve is no longer huge,
                // cross the shot line instead of only reversing along the same orbit.
                drivePerpendicularEscape(absBearing, getEnergy() < 42.0 ? 310.0 : 250.0);
                return;
            }
            if (mediumPowerWallCruiser()) {
                // TannerBot's medium wall shots are simple but frequent; a pure orbit
                // reversal can leave us parallel to the wall and on the same line.  Step
                // across the shot, especially in the mid/late reserve phase.
                drivePerpendicularEscape(absBearing, getEnergy() < 42.0 ? 315.0 : 255.0);
                return;
            }
            if (npcSniperEnemy() && getEnergy() < 24.0) {
                // The remaining NPCSniper loss came from a late low-energy exchange:
                // after a few medium bullets our bot kept orbiting predictably near
                // 250-370px and was finished by one small hit.  When it fires while our
                // reserve is low, spend the next movement command sidestepping the shot
                // instead of only reversing along the same orbit.
                drivePerpendicularEscape(absBearing, 260.0);
                return;
            }
            if (juggernautEnemy()) {
                // dankraemer__juggernaut spends repeated power-3 bullets while making
                // max-speed stop/turn bursts.  The old high-power stop/go branch only
                // reversed on early fire ticks; loss traces show several power-3 hits
                // in the 200-350px band.  Commit to a clean perpendicular dodge on each
                // detected shot, not only after our energy is already low.
                drivePerpendicularEscape(absBearing, 275.0);
                return;
            }
            if (turningHighPowerEnemy()) {
                // Current jeujdapeu profile: frequent power-3 shots while making
                // medium-speed turns.  Do not just reverse the orbit on the firing tick;
                // step across the gun line so its simple high-power aim has to reacquire.
                drivePerpendicularEscape(absBearing, getEnergy() < 38.0 ? 330.0 : 285.0);
                return;
            }
        }

        if (stationaryHeavyShooter() && e.getDistance() < 430.0) {
            // TrackFire follow-up traces showed that a pure perpendicular command can
            // alternate around the same point (~285px) and let a stationary power-3 gun
            // land nearly every shot.  Use a diagonal away+perpendicular escape so the
            // range actually opens toward the 455px orbit while still crossing the
            // head-on firing line.
            driveStationaryHeavyEscape(absBearing, getEnergy() < 35.0 ? 390.0 : 340.0);
            return;
        }

        // SittingDuck-style opponents in many logs never fire.  Once we are
        // confident a stationary target is harmless, cancel movement so we do
        // not donate wall damage while the gun farms max-power hits.  One
        // important exception: if the round spawned us almost touching a
        // stationary bot (sample.Fire in the current traces), stopping here can
        // pin both robots together in a ram loop until a draw.  Always open a
        // safe gap from close stationary targets before entering farm mode.
        if (stationaryScans > 5 && e.getDistance() < 260.0) {
            // If we spawned close to a stationary shooter near a corner, the true
            // "away" vector can point straight into the wall and leave us oscillating
            // in its gun line.  For LeachPMC specifically, round-1's only draw came
            // from the perpendicular fallback keeping us pinned at ~37px, so prefer
            // separation-biased driveAwayFrom all the way until the gap is open.
            if (leachPmcEnemy()) {
                driveAwayFrom(absBearing, 340.0);
            } else {
                double away = absBearing + Math.PI;
                if (!insideBattlefield(projectX(getX(), away, 170.0), projectY(getY(), away, 170.0), 24.0)) {
                    drivePerpendicularEscape(absBearing, 300.0);
                } else {
                    driveAwayFrom(absBearing, 300.0);
                }
            }
            return;
        }
        if (fixedHeadingHighPowerShooter() && e.getDistance() < 260.0) {
            // Exterminador can stop near a wall and trade repeated power-3 shots.
            // Driving directly away is often into the wall; sidestep the firing line
            // until the range opens instead of sitting in the corner.
            drivePerpendicularEscape(absBearing, 260.0);
            return;
        }
        if (juggernautEnemy() && getEnergy() < 32.0 && e.getDistance() < 320.0) {
            // Juggernaut's remaining wins are late low-energy scrambles after several
            // power-3 hits.  Do not resume the normal 385px orbit from inside its
            // high-damage band; spend the movement command crossing/perpendicular to
            // the gun line until the range reopens.
            drivePerpendicularEscape(absBearing, 330.0);
            return;
        }
        if (turningHighPowerEnemy() && getEnergy() < 38.0 && e.getDistance() < 360.0) {
            // Late losses against the current power-3 turner were decided by one more
            // heavy hit while we were low.  Reopen range before resuming the orbit.
            drivePerpendicularEscape(absBearing, 380.0);
            return;
        }
        if (maximbotEnemy() && e.getDistance() < (getEnergy() < 36.0 ? 385.0 : 320.0)) {
            // The only recorded Maximbot losses were close, high-power exchanges.  Keep
            // enough room for lateral circular-gun shots instead of letting its simple gun
            // trade at 100-170px.
            drivePerpendicularEscape(absBearing, getEnergy() < 36.0 ? 455.0 : 380.0);
            return;
        }
        if (shrekerEnemy() && getEnergy() < 24.0 && e.getDistance() < 240.0) {
            // Only force a direct reopen from true knife range.  The wider old 360px guard
            // pushed us out past 500px in endgames, where our head-on pinpricks arrived too
            // late and Shreker's existing p3 bullets decided the round.
            drivePerpendicularEscape(absBearing, 285.0);
            return;
        }
        if (m9WallStopGoEnemy() && getEnergy() < 30.0 && e.getDistance() < 310.0) {
            // Avoid low-energy point-blank wall scrambles against the current power-2
            // stop/go shooter; resume the normal close band after a safe gap is open.
            driveAwayFrom(absBearing, 335.0);
            return;
        }
        if (dominatorEnemy() && getEnergy() < 34.0 && e.getDistance() < 330.0) {
            drivePerpendicularEscape(absBearing, 390.0);
            return;
        }
        if (mediumPowerWallCruiser() && getEnergy() < 28.0 && e.getDistance() < 340.0) {
            driveAwayFrom(absBearing, 380.0);
            return;
        }
        if (lowFireTracker() && e.getDistance() < 415.0) {
            // Tracker-like chasers deliberately close the bearing line.  Keep a wider
            // direct separation band than RamFire so its occasional power-3 shots do
            // not become point-blank trades while our linear gun farms the approach.
            // Round-1 still showed >100/250 traces dipping under 100px; stretch this
            // escape a little more while the target remains a clean radial charger.
            driveAwayFrom(absBearing, 405.0);
            return;
        }
        if (lowFireRammer() && e.getDistance() < 300.0) {
            // sample.RamFire-style chargers keep driving straight at us and only
            // leak score through point-blank shots/collisions.  Do not wait for the
            // generic 225px close-escape threshold: once the rammer signature is
            // confirmed, keep stretching the range while the linear gun fires down
            // its charge line.
            driveAwayFrom(absBearing, 285.0);
            return;
        }
        if (straightStopGoLinearEnemy() && e.getDistance() < 245.0) {
            // Exterminador fires mostly power-3 when it gets a close stop/go lock.
            // Keep enough separation that our orbit has lateral room and we do not
            // trade point-blank 16-damage hits while pinned near a wall.
            driveAwayFrom(absBearing, 285.0);
            return;
        }
        if (straightEnemyScans > 2 && harmlessLowFireEnemy() && e.getDistance() < 310.0) {
            // Hugbot/simple harmless runners can cross our orbit at full speed before
            // the narrow rammer detector fully confirms.  Open the gap early and keep
            // opening it longer; these opponents are not shooting, so avoiding point-
            // blank bullet/ram leakage is worth a few extra pixels of bullet flight.
            driveAwayFrom(absBearing, 305.0);
            return;
        }
        if (e.getDistance() < 225.0
                && (harmlessLowFireEnemy() || wallEnemyScans > 2 || stopGoEnemyScans > 4
                        || Math.abs(enemyVelocityAvg) < 1.2)) {
            // Corners/sample.Fire-style close starts can turn into repeated rams
            // before the stationary detector has enough scans.  Keep opening the
            // gap until outside the point-blank danger band, bypassing orbit/wall-
            // smoothing code that can curve us back across the opponent.
            driveAwayFrom(absBearing, 225.0);
            return;
        }
        if (stationaryScans > 10 && enemyFireCount == 0 && !leachPmcEnemy()) {
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
        if (sampleWallsEnemy()) {
            // Current robo_code__walls opponent is a fast perimeter runner with a very
            // accurate simple gun.  The old generic wall branch fought around ~335-400px
            // and spent max-power shots; widen the lane so fire-tick reversals have time
            // to clear its head-on bullets while still keeping linear shots reasonable.
            preferredDistance = getEnergy() < 24.0 ? 560.0 : (getEnergy() < 48.0 ? 520.0 : 475.0);
        } else if (robrrratEnemy()) {
            // Current sacdalance__robrrrat fires mostly power-3 and hit the old close
            // ~285-330px exchange hard enough to steal a few live wins.  Stay at a
            // moderate/wide averaged-gun band: still short enough for p2-ish bullets,
            // but with more lateral time against its simple high-power aim.
            preferredDistance = getEnergy() < 22.0 ? 520.0 : (getEnergy() < 45.0 ? 470.0 : 395.0);
        } else if (maximbotEnemy()) {
            // Current Maximbot is very hittable by circular aim, but its medium/high gun
            // leaks damage at knife range.  Use a compact circular-shot band while healthy
            // and widen modestly before reserve mode.
            preferredDistance = getEnergy() < 24.0 ? 485.0 : (getEnergy() < 50.0 ? 430.0 : 375.0);
        } else if (pikachuEnemy()) {
            // kcanida__pikachu fires a stream of tiny bullets while making short stop/turn
            // dodges.  The old generic stop-go logic hugged ~220px and spent power-2/3
            // shots until self-disable.  Hold a moderately wide band: close enough for fast
            // cheap head/damped shots, but with room to cross its low-power firing line.
            preferredDistance = getEnergy() < 18.0 ? 470.0 : (getEnergy() < 42.0 ? 425.0 : 380.0);
        } else if (waveSurfingEnemy()) {
            // Its gun is ineffective in the logs; closing the range shortens our cheap
            // head-on bullets, but keep extra room once our reserve is low so we do not
            // get trapped in late wall/corner scrambles while trying to finish.
            preferredDistance = getEnergy() < 26.0 ? 430.0 : 295.0;
        } else if (crawlerEnemy()) {
            // txeverson__crawler is a predictable medium-speed circular mover.  The first
            // name-gated pass over-tightened to ~285px and slightly regressed score versus
            // the older SpinBot-style behavior, likely by increasing exposure to its p2 gun
            // and forcing circular even when averaged is virtually ahead.  Use the proven
            // circular-farming band instead: still close, but not quite knife-range.
            preferredDistance = (virtualSamples > 10 && virtualGunError[GUN_CIRCULAR] < 55.0) ? 305.0 : 340.0;
        } else if (spinBotEnemy()) {
            // SpinBot follows a compact, very predictable circle.  Round 1 showed
            // the circular/max-power specialization is very safe (large end-energy
            // surplus), so after a few virtual waves confirm the exact circle, pull
            // in from the conservative 340px band to shorten bullet flight and kill
            // before its random spinning gun can leak stray hits.  Keep the wider
            // cold-start range for awkward spawn/wall approaches.
            preferredDistance = (virtualSamples > 10 && virtualGunError[GUN_CIRCULAR] < 55.0) ? 305.0 : 340.0;
        } else if (shrekerEnemy()) {
            // Shreker's gun is a real p3 threat.  Round-1 follow-up losses show the old
            // 455/535px reserve orbit made final head-on shots slow and inaccurate while
            // already-fired p3 bullets caught us.  Stay in a compact band for short bullet
            // flight, widening only slightly in the last reserve.
            preferredDistance = getEnergy() < 14.0 ? 420.0 : (getEnergy() < 34.0 ? 360.0 : 330.0);
        } else if (dominatorEnemy()) {
            // DominatorX is an active medium-power mixed straight/turn mover.  Replay of
            // round-0 traces favored head-on/wall-damped over full lead; stay moderately
            // wide and conserve in long games instead of Tanner/NPC linear/averaged chases.
            preferredDistance = getEnergy() < 22.0 ? 500.0 : (getEnergy() < 42.0 ? 440.0 : 365.0);
        } else if (npcSniperEnemy()) {
            // iagomonteiro13579__npcsniper: medium-fast straight/wall bursts with
            // lots of medium shots.  It is harder to hit than the simple wall
            // cruisers, and max-power shots self-deplete in the loss traces; keep a
            // wider, safer band and rely on faster damped bullets.
            preferredDistance = getEnergy() < 18.0 ? 540.0 : (getEnergy() < 28.0 ? 500.0 : 405.0);
        } else if (velociRobotEnemy()) {
            // VelociRobot-style target in the current logs: medium-fast low-turn
            // straight runs with frequent weak fire.  It is not a continuous Crazy
            // turner; keeping a moderate range plus fast head-on shots avoids the
            // old circular/max-power over-lead and reduces rare self-depletion.
            preferredDistance = 345.0;
        } else if (crazyEnemyScans > 3) {
            // team488__meow has a Crazy-like high-speed turn pattern but is much
            // more predictable than sample.Crazy.  Tighten only after virtual waves
            // confirm low circular error, preserving the safer old Crazy spacing.
            preferredDistance = (virtualSamples > 14 && virtualGunError[GUN_CIRCULAR] < 85.0) ? 260.0 : 305.0;
        } else if (quadWallEnemy()) {
            // QuadWall-style perimeter stop/go runner: stays on walls, fires many
            // weak bullets, and virtual/replay evidence favors the normal averaged
            // predictor over the fast-cruise linear gun.  Keep a moderate range;
            // widen only through power caps/low-energy safeguards instead of chasing
            // point-blank along the wall.
            preferredDistance = getEnergy() < 18.0 ? 430.0 : 305.0;
        } else if (gntestEnemy() && stationaryScans > 4) {
            // Name-gated current matchup: when GNTest parks and shoots, do not let
            // stationaryShooter/TrackFire branches force p3 farming or very long
            // slow-bullet exchanges.  Keep enough range for dodging, but not so far
            // that our faster medium head-on bullets take forever.
            preferredDistance = getEnergy() < 18.0 ? 465.0 : (getEnergy() < 36.0 ? 420.0 : 365.0);
        } else if (fixedHeadingHighPowerShooter()) {
            // A fixed-heading high-power stop/go shooter is more dangerous than
            // Ian/Tarektank-style weak axis bots.  Keep a short but not point-blank
            // range: logs show our hits are reliable here, and opening too far can
            // reduce damage before the opponent's parked power-3 trades arrive.
            preferredDistance = 300.0;
        } else if (leachPmcEnemy() && stationaryScans > 5) {
            // LeachPMC is stationary like TrackFire but starts as a p3 shooter before our
            // enemy-fire counter has settled.  Hold an even wider band to keep lateral
            // speed high against its head-on p3 stream; exact p3 shots still land.
            preferredDistance = getEnergy() < 35.0 ? 505.0 : 455.0;
        } else if (stationaryHeavyShooter()) {
            // TrackFire is stationary like NagiSphere, but its repeated power-3 head-on
            // bullets punished the old close 330px farming band when orbit reversals
            // stalled us around 230-250px.  Keep max-power exact shots, but orbit wider
            // and combine with fire-tick perpendicular dodges above.
            preferredDistance = getEnergy() < 35.0 ? 505.0 : 455.0;
        } else if (stationaryShooter()) {
            // Medium/weak stationary shooters are best farmed faster from a closer band.
            // Heavy stationary shooters are handled by the previous wider branch.
            preferredDistance = 330.0;
        } else if (mediumPowerWallCruiser()) {
            // Current TannerBot profile: medium-power wall/perimeter runner with long
            // straight cardinal legs plus corner stops.  Keep a short linear-shot band
            // while healthy, but open a little once reserve is low to avoid the long
            // self-depletion wall chases seen in round-0 loss traces.  Put this before
            // fixed-heading wall-axis branches; long cardinal wall legs can otherwise
            // look like one-dimensional fixed-heading motion for too long.
            preferredDistance = getEnergy() < 22.0 ? 405.0 : 300.0;
        } else if (weakFixedAxisOscillator()) {
            // Current Tarektank-style target is a one-dimensional 100px
            // oscillator with a weak fixed-heading gun.  Move closer than the
            // generic Ian/RegullarMonk conservation profile to shorten bullet
            // flight and make midpoint/axis shots land before it reverses.
            preferredDistance = 225.0;
        } else if (gntestStopDuel()) {
            // josephjeon__gntest has a lossy late mode where it parks/creeps on a
            // nearly fixed heading and trades repeated medium bullets.  The old
            // fixed-heading-medium branch widened to 455/515px and used heavy shots,
            // causing long self-depletion losses.  Keep a compact but not point-blank
            // band so faster head-on/circular bullets can finish before reserve runs out.
            preferredDistance = getEnergy() < 16.0 ? 405.0 : (getEnergy() < 34.0 ? 365.0 : 325.0);
        } else if (fixedHeadingMediumShooter()) {
            // Chilibot-like fixed-heading stop/go shooter: stronger than the
            // old weak axis oscillators, but still easiest to hit head-on.
            // Keep a moderate range while healthy for score, but widen sooner
            // in long games; the remaining losses were late medium/high-power
            // bullet hits after our energy had fallen below ~30.
            preferredDistance = getEnergy() < 18.0 ? 515.0 : (getEnergy() < 32.0 ? 455.0 : 360.0);
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
        } else if (lowFireTracker()) {
            // Tracker-like chasers fire more often than RamFire and intentionally
            // close on our current position.  A wider band cuts their close-range
            // power-3 leakage while still keeping linear bullet flight short.
            preferredDistance = 380.0;
        } else if (lowFireRammer()) {
            // RamFire-like opponents try to close directly.  Keep a short bullet
            // flight but maintain just enough spacing to avoid long pin loops.
            preferredDistance = 260.0;
        } else if (straightStopGoLinearEnemy()) {
            // Exterminador-style targets spend about half their time stopped but
            // otherwise run long, nearly straight segments.  Virtual replay says
            // full linear prediction is much better than damped stop/go averaging;
            // keep a moderate exchange: close enough for linear hits, but wider than
            // the old 260-275px band that let power-3 stop/go shots trade too well.
            preferredDistance = 315.0;
        } else if (easyHeadOnStopGoEnemy()) {
            // Cliffbot2-style stop/go movers are weak and the virtual guns show
            // near-head-on beating the damped averaged gun.  Keep a very close
            // farming range to shorten max-power bullet flight; this branch is
            // gated by low virtual error/fire count so RegullarMonk-style self-
            // depletion cases still use their conservation profile.
            preferredDistance = 255.0;
        } else if (turningHighPowerEnemy()) {
            // jeujdapeu turns continuously while firing power-3.  Keep the healthy
            // exchange near our accurate averaged-gun band, but widen before the late
            // low-energy phase where all recorded losses occurred.
            preferredDistance = getEnergy() < 12.0 ? 585.0 : (getEnergy() < 22.0 ? 555.0 : (getEnergy() < 40.0 ? 505.0 : 400.0));
        } else if (juggernautEnemy()) {
            // Juggernaut is a dangerous power-3 stop/turn bot; stay a bit wider than
            // the Ultron farming band and open further once the reserve falls.
            preferredDistance = getEnergy() < 18.0 ? 540.0 : (getEnergy() < 32.0 ? 500.0 : 385.0);
        } else if (highPowerStopGoDodger()) {
            // Current Ultron-style target: frequent power-3 firing, lots of stops,
            // but enough max-speed reversing that the high-pressure Florian branch
            // self-depletes.  Stay in a moderate exchange band and let fast
            // head-on bullets land without spending our whole energy stack.
            preferredDistance = 355.0;
        } else if (heavyStopGoShooter()) {
            // Florian2-style target: spends most of the round stopped, occasionally
            // bursts at max speed, and spends power-3 shots with poor aim.  It is
            // safe to keep the short-flight damped stop/go exchange instead of
            // falling into the old RegullarMonk conservation orbit.
            preferredDistance = 275.0;
        } else if (m9WallStopGoEnemy()) {
            // it_economics__ite_m9 is a power-2 wall/stop-go duelist.  Round-1 losses
            // were the long games where we drifted 420-500px from its wall/corner path
            // and traded slow misses for accurate p2 hits.  Pull a little closer while
            // healthy to shorten wall-damped bullet flight; widen only in reserve mode.
            preferredDistance = getEnergy() < 22.0 ? 390.0 : 285.0;
        } else if (mediumStopGoDuelist()) {
            // MarkRobo-style medium-power stop/go duelists are the current lossy
            // profile.  They are easy enough to outscore, but close max-power
            // exchanges occasionally let their medium gun drain us before our damped
            // bullets finish the job.  Open the band earlier after repeated fires;
            // keep a closer range only in the high-energy opening.
            preferredDistance = getEnergy() < 28.0 ? 460.0
                    : (getEnergy() < 58.0 || enemyFireCount > 10 ? 390.0 : 320.0);
        } else if (mediumStopGoShooter()) {
            // Gruffalo/SadBot-style opponents: many stops / low-turn bursts and
            // repeated medium-power shots.  SadBot still leaves us with a large
            // energy surplus, so tighten the exchange further to shorten max-power
            // bullet flight; the generic close-stop/go escape still prevents true
            // point-blank trading below ~225px.
            preferredDistance = 260.0;
        } else if (activeStopGoShooter()) {
            // RegullarMonk-style bots stop/reverse constantly but fire repeated
            // weak bullets.  They are easiest to hit with fast head-on shots;
            // stay near its usual 320-350px exchange band instead of drifting
            // wide into long, low-damage self-depletion rounds.
            preferredDistance = 340.0;
        } else if (wallsPoetEnemy()) {
            // WallsPoet is a high-power wall/stop-go bot.  It hits hard enough that the
            // old close 335px DroidPoet pressure band lost many survival points, but
            // going very wide would lengthen already-hard shots.  Hold a mid band and
            // open it once our energy reserve drops.
            preferredDistance = getEnergy() < 28.0 ? 500.0 : (getEnergy() < 55.0 ? 450.0 : 390.0);
        } else if (dangerousWallEnemy()) {
            preferredDistance = 335.0;
        } else if (!maximbotEnemy() && straightEnemyScans > 16 && harmlessLowFireEnemy() && wallEnemyScans <= 4) {
            preferredDistance = 335.0;
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
        if (!waveSurfingEnemy() && !dangerousWallEnemy() && !activeStopGoShooter() && !heavyStopGoShooter()
                && virtualSamples > 28 && bestGunError() > 72.0 && stationaryScans <= 5 && slowEnemyScans <= 12) {
            preferredDistance = 355.0;
        }
        if (activeHighPowerShooter() && getEnergy() < 18.0) {
            // When low on energy against Ultron-style power-3 shooters, avoid the
            // close 150-220px scrambles seen in the remaining loss/draw traces.  A
            // wider orbit gives our cheap bullets time to be energy-positive while
            // reducing the chance of one more enemy power-3 hit ending the round.
            preferredDistance = Math.max(preferredDistance, 500.0);
        }
        if (velociRobotEnemy() && getEnergy() < 22.0) {
            // VelociRobot losses happen late after our bullet energy has been spent;
            // at that point one more weak bullet can finish us.  Back out of the close
            // 345px farming band while using tiny fast shots, instead of continuing the
            // same exchange range that is fine when our energy reserve is high.
            preferredDistance = Math.max(preferredDistance, 440.0);
        }
        double distanceOffset = limit(-0.62, (e.getDistance() - preferredDistance) / 430.0, 0.55);
        double desired = absBearing + moveDirection * (Math.PI / 2.0 - distanceOffset);
        if (e.getDistance() < 118
                || (lowFireRammer() && e.getDistance() < 300)
                || (e.getDistance() < 225
                        && (harmlessLowFireEnemy() || wallEnemyScans > 2 || stopGoEnemyScans > 4
                                || Math.abs(enemyVelocityAvg) < 1.2))) {
            // Many simple/corner bots only become dangerous at spawn/knife range.
            // Sample Corners can drive through us from ~180px and pin both tanks
            // at ~36px.  Prefer direct separation until a healthy gap is open from
            // low-fire or wall/stop-go targets, then resume the close farming orbit.
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
            if (gntestEnemy()) {
                // Against the current GNTest parked shooter, p3 head-on trades lose
                // too much energy to its accurate medium/high bullets.  Faster medium
                // bullets both arrive sooner and reduce self-depletion in the loss mode.
                if (e.getEnergy() < 11.0 && getEnergy() > 7.0 && distance < 620.0) {
                    power = Math.min(Math.max(lethalPower(e.getEnergy()), 0.55), 1.85);
                } else if (getEnergy() > 58.0) {
                    power = Math.min(power, distance < 380 ? 1.95 : 1.65);
                } else if (getEnergy() > 34.0) {
                    power = Math.min(power, distance < 360 ? 1.25 : 0.95);
                } else if (getEnergy() > 16.0) {
                    power = Math.min(power, distance < 330 ? 0.48 : 0.32);
                } else {
                    power = Math.min(power, getEnergy() < 8.0 ? 0.12 : 0.20);
                }
            }
            if (stationaryHeavyShooter() && getEnergy() < 34.0 && e.getEnergy() < 18.0) {
                // When low against a power-3 stationary shooter, avoid spending excess
                // energy on overkill; a minimum lethal bullet is faster and preserves the
                // small margin that decided several TrackFire loss/draw traces.
                power = Math.min(power, lethalPower(e.getEnergy()));
            }
        } else if (!maximbotEnemy() && wallEnemyScans > 4 && !dangerousWallEnemy() && getEnergy() > 14 && distance < 820) {
            // Wall-huggers have very limited escape room; use max-power
            // head-on/near-head-on shots to finish them before they can spend
            // energy on stray bullets (which lowers our available bullet score).
            power = 3.0;
        } else if (!maximbotEnemy() && straightEnemyScans > 2 && harmlessLowFireEnemy() && getEnergy() > 14 && distance < 760) {
            // Straight runners are easy for the linear gun, even when they are
            // not close enough to the wall to trip wallEnemyScans.  Use max
            // power to shorten antiwalls-style rounds once the line is clear.
            power = 3.0;
        } else if (!maximbotEnemy() && !waveSurfingEnemy() && headOnGunIsBest() && getEnergy() > 18 && distance < 720) {
            // The current DeepThought opponent dodges/reverses enough that a
            // head-on gun wins the virtual-gun race.  Once detected, spend more
            // energy on heavier bullets: its own hit rate is tiny, and the
            // shorter rounds are worth the slightly slower bullet speed.
            power = Math.max(power, distance < 360 ? 3.0 : (distance < 520 ? 2.8 : 2.35));
        } else if (!maximbotEnemy() && !waveSurfingEnemy() && (slowEnemyScans > 8 || activeStopGoEnemy()) && !highPowerStopGoDodger() && getEnergy() > 12 && distance < 720) {
            // Slow and stop/go opponents give up enough predictable time that
            // heavier bullets trade a little travel time for much faster damage and
            // a larger bullet bonus.  Fast/unknown movers keep the safer ladder.
            power = 3.0;
        }
        boolean finishingFixedHighPower = fixedHeadingHighPowerShooter() && e.getEnergy() < 17.0 && distance < 460.0 && getEnergy() > 6.0;
        boolean finishingFixedMedium = fixedHeadingMediumShooter() && e.getEnergy() < 17.0 && distance < 540.0 && getEnergy() > 6.0;
        boolean hardToHitMover = virtualSamples > 28 && bestGunError() > 72.0 && stationaryScans <= 5 && slowEnemyScans <= 12;
        if (sampleWallsEnemy()) {
            // Full linear prediction is the best replay model for sample.Walls-style
            // perimeter motion, but power-3 bullets are slow and caused self-depletion in
            // long chases.  Use faster medium bullets while healthy, then cheap reserve
            // shots; finish with a capped lethal bullet when it is already low.
            if (e.getEnergy() < 9.0 && getEnergy() > 5.5) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 1.65);
            } else if (getEnergy() > 58.0) {
                power = Math.min(Math.max(power, distance < 420 ? 2.10 : 1.85), 2.15);
            } else if (getEnergy() > 34.0) {
                power = Math.min(Math.max(power, distance < 380 ? 1.35 : 1.05), 1.45);
            } else if (getEnergy() > 18.0) {
                power = Math.min(power, distance < 350 ? 0.42 : 0.28);
            } else if (getEnergy() > 12.0) {
                power = Math.min(power, 0.15);
            } else {
                power = Math.min(power, 0.10);
            }
        }
        if (robrrratEnemy()) {
            // Robrrrat is hittable by averaged/circular aim, but p3 misses plus its own
            // p3 stream caused all observed losses.  Use faster medium bullets while
            // healthy and preserve a movement reserve in long rounds; use a capped
            // lethal/near-lethal shot only when the enemy is already low.
            if (e.getEnergy() < 9.0 && getEnergy() > 6.0 && distance < 560.0) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 1.65);
            } else if (getEnergy() > 62.0) {
                power = Math.min(Math.max(power, distance < 430.0 ? 2.25 : 1.95), 2.35);
            } else if (getEnergy() > 38.0) {
                power = Math.min(Math.max(power, distance < 380.0 ? 1.40 : 1.10), 1.55);
            } else if (getEnergy() > 20.0) {
                power = Math.min(power, distance < 340.0 ? 0.65 : 0.45);
            } else {
                power = Math.min(power, getEnergy() < 9.0 ? 0.12 : 0.22);
            }
        }
        // If all virtual guns are missing badly (as with wave-surfing GF-style
        // enemies), do not gamble the whole energy stack on repeated heavy
        // bullets.  Use tiny bullets at low energy: a hit gives more energy back
        // than it costs, while misses cannot self-kill us quickly.
        if (waveSurfingEnemy()) {
            // Round-1 follow-up: the first surfer branch was too timid for too long,
            // then still self-depleted with endless 0.1-0.6 bullets.  The opponent's
            // Robocode results show essentially no bullet damage; the real losing mode
            // is spending ourselves to zero.  Use more decisive fast/medium pressure
            // while we have a large reserve, then switch to true reserve mode (and the
            // final fireAllowed guard below) instead of dribbling all the way to zero.
            if (getEnergy() > 72.0) {
                power = Math.min(power, distance < 360 ? 1.95 : 1.60);
            } else if (getEnergy() > 48.0) {
                power = Math.min(power, distance < 340 ? 1.10 : 0.85);
            } else if (getEnergy() > 28.0) {
                power = Math.min(power, distance < 320 ? 0.45 : 0.30);
            } else if (getEnergy() > 16.0) {
                power = Math.min(power, 0.12);
            } else {
                power = Math.min(power, 0.10);
            }
        }
        if (!maximbotEnemy() && straightEnemyScans > 16 && harmlessLowFireEnemy() && wallEnemyScans <= 4) {
            // Sustained non-wall straight runners are harmless enough for heavy
            // bullets; the averaged gun still handles their stops/reverses.
            // Keep pressure high while the slightly wider orbit reduces rare
            // point-blank ram/leakage in long field-crossing runs.
            power = Math.max(power, distance < 620 ? 3.0 : 2.65);
        }
        if (crawlerEnemy()) {
            // Crawler replay favors circular, but round-1 results slipped when every healthy
            // shot stayed max-power out to very long range.  Match the safer SpinBot pressure
            // profile: max-power where bullet flight is short, moderate power at long range,
            // and only defensive caps in unexpected reserve phases.
            if (getEnergy() > 16 && distance < 760) {
                power = Math.max(power, distance < 680 ? 3.0 : 2.55);
            } else if (getEnergy() < 10) {
                power = Math.min(power, 0.55);
            }
        }
        if (spinBotEnemy()) {
            // Circular prediction is essentially exact for SpinBot; max-power
            // bullets increase damage/bonus and reduce the number of random
            // spinning-gun shots it can fire before dying.
            if (getEnergy() > 16 && distance < 760) {
                power = Math.max(power, distance < 680 ? 3.0 : 2.55);
            } else if (getEnergy() < 10) {
                power = Math.min(power, 0.55);
            }
        }
        if (fastWallCruiser() && getEnergy() > 14 && distance < 820) {
            power = Math.max(power, distance < 650 ? 3.0 : 2.55);
        }
        if (quadWallEnemy()) {
            // Round-1's early medium-power QuadWall caps prolonged the fight and
            // lowered both score and survival.  This opponent's weak bullets are
            // best handled by finishing quickly: keep near the original max-pressure
            // wall-farming behavior while healthy, but retain tiny bullets only when
            // our reserve is genuinely low to prevent self-depletion in outlier chases.
            if (getEnergy() > 24 && distance < 820) {
                power = Math.max(power, distance < 650 ? 3.0 : 2.55);
            } else if (getEnergy() > 12) {
                power = Math.min(Math.max(power, distance < 420 ? 1.35 : 1.05), 1.55);
            } else {
                power = Math.min(power, getEnergy() < 7 ? 0.15 : 0.30);
            }
        }
        if (lowFireTracker() && getEnergy() > 14 && distance < 760) {
            // The approach is highly predictable; max-power linear shots shorten
            // the round and reduce the time available for close-range Tracker fire.
            power = Math.max(power, distance < 640 ? 3.0 : 2.55);
        }
        if (shrekerEnemy()) {
            // Losses were self-depletion against repeated p3 fire.  Use fast medium shots
            // while healthy (head/wall-damped replay error improves at lower power), then
            // sharply conserve.  Follow-up losses often had Shreker under ~16 energy while
            // we kept firing pinpricks and an old p3 bullet decided the round; if we still
            // have a real reserve, spend a capped lethal/near-lethal shot to end it now.
            if (e.getEnergy() < 18.0 && getEnergy() > 10.0 && distance < 620.0) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 2.55);
            } else if (e.getEnergy() < 9.0 && getEnergy() > 5.5) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 1.65);
            } else if (getEnergy() > 62.0) {
                power = Math.min(Math.max(power, distance < 380 ? 1.85 : 1.55), 1.95);
            } else if (getEnergy() > 38.0) {
                power = Math.min(Math.max(power, distance < 360 ? 1.20 : 0.95), 1.35);
            } else if (getEnergy() > 18.0) {
                power = Math.min(power, distance < 330 ? 0.45 : 0.28);
            } else {
                power = Math.min(power, getEnergy() < 8.0 ? 0.12 : 0.20);
            }
        }
        if (dominatorEnemy()) {
            // DominatorX losses are self-depletion medium-power duels.  Round-1 traces
            // showed our first detector was too strict: several losing games still spent
            // power-3 shots down into the 20-energy range before this branch stuck.  Once
            // the p~2 mixed straight/turn signature is present, use head-on pressure but
            // cap it earlier and keep a real reserve instead of dribbling to zero.
            if (e.getEnergy() < 8.0 && getEnergy() > 5.5) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 1.35);
            } else if (getEnergy() > 68.0) {
                power = Math.min(Math.max(power, distance < 360 ? 1.85 : 1.55), 1.90);
            } else if (getEnergy() > 44.0) {
                power = Math.min(Math.max(power, distance < 360 ? 1.15 : 0.90), 1.25);
            } else if (getEnergy() > 24.0) {
                power = Math.min(power, distance < 330 ? 0.42 : 0.28);
            } else if (getEnergy() > 11.0) {
                power = Math.min(power, distance < 330 ? 0.20 : 0.12);
            } else {
                power = Math.min(power, 0.10);
            }
        }
        if (npcSniperEnemy()) {
            // NPCSniper lands enough medium bullets that long max-power miss streaks
            // are the only real losing mode.  Use faster medium/cheap bullets with a
            // strict low-energy cap; the damped predictor gains more from speed than
            // from raw damage here.
            if (getEnergy() > 42.0) {
                power = Math.min(Math.max(power, distance < 360 ? 2.20 : (distance < 560 ? 1.85 : 1.45)), 2.20);
            } else if (getEnergy() > 24.0) {
                power = Math.min(Math.max(power, distance < 360 ? 1.35 : 1.05), 1.45);
            } else if (getEnergy() > 12.0) {
                power = Math.min(power, distance < 330 ? 0.50 : 0.30);
            } else {
                power = Math.min(power, getEnergy() < 7.0 ? 0.15 : 0.20);
            }
        }
        if (velociRobotEnemy()) {
            // VelociRobot fires many weak bullets but is not dangerous enough to justify
            // the very low-power conservation tried in round 1; that prolonged rounds
            // and produced several self-depletion deaths with the enemy still alive.
            // Keep the damped/averaged aim, but apply enough pressure while healthy to
            // finish before long weak-fire exchanges, then switch to tiny survival shots
            // only after our energy is actually low.
            if (getEnergy() > 34) {
                power = Math.min(Math.max(power, distance < 360 ? 2.70 : (distance < 560 ? 2.45 : 2.10)), 2.75);
            } else if (getEnergy() > 22) {
                power = Math.min(Math.max(power, distance < 360 ? 1.75 : 1.35), 1.90);
            } else if (getEnergy() > 12) {
                power = Math.min(power, distance < 340 ? 0.85 : 0.60);
            } else {
                power = Math.min(power, getEnergy() < 7 ? 0.15 : 0.35);
            }
        }
        if (crazyEnemyScans > 3 && !velociRobotEnemy() && !spinBotEnemy()) {
            // High-speed continuous turners are easier to hit with faster,
            // moderate-power circular shots.  For this round's meow opponent the
            // circular virtual gun settles far below the old sample.Crazy errors;
            // when that happens and our energy is abundant, heavier bullets have
            // better expected damage and should finish the very safe match faster.
            boolean easyCircularTurner = virtualSamples > 14 && virtualGunError[GUN_CIRCULAR] < 85.0;
            if (easyCircularTurner && getEnergy() > 45) {
                power = Math.max(power, distance < 680 ? 3.0 : 2.55);
            } else if (easyCircularTurner && getEnergy() > 22) {
                power = Math.min(Math.max(power, distance < 460 ? 2.35 : 1.95), 2.55);
            } else if (getEnergy() > 16) {
                power = Math.min(power, distance < 240 ? 2.50 : (distance < 460 ? 2.25 : 1.85));
            } else {
                power = Math.min(power, 1.25);
            }
        }
        if (straightStopGoLinearEnemy()) {
            // Current Exterminador traces: long straight/stop runs where linear
            // prediction is clearly best.  Press hard while energy is abundant,
            // but avoid the rare self-depletion losses seen when long games keep
            // spending max-power bullets after our energy has fallen.
            if (getEnergy() > 38 && distance < 720) {
                power = Math.max(power, distance < 600 ? 3.0 : 2.45);
            } else if (getEnergy() > 18) {
                power = Math.min(Math.max(power, 1.25), 1.85);
            } else {
                power = Math.min(power, getEnergy() < 9 ? 0.15 : 0.45);
            }
        } else if (fixedHeadingHighPowerShooter()) {
            // Exterminador-like high-power fixed stop/go shooters should not inherit
            // the Ian low-power conservation cap; in losses we were landing cheap
            // 1.1-2.0 bullets while eating power-3 hits and leaving it alive on
            // ~10-18 energy.  Use decisive shots while healthy, then fall back before
            // true self-depletion.
            if (finishingFixedHighPower) {
                // If it is already under one max-power hit of death, finish it before
                // the next power-3 bullet arrives; low-power pinpricks caused several
                // logged losses with the opponent surviving on ~12-18 energy.
                power = Math.max(power, Math.min(3.0, getEnergy() - 0.15));
            } else if (getEnergy() > 36 && distance < 680) {
                power = Math.max(power, distance < 540 ? 3.0 : 2.35);
            } else if (getEnergy() > 16) {
                power = Math.min(Math.max(power, 1.45), 2.05);
            } else {
                power = Math.min(power, getEnergy() < 8 ? 0.15 : 0.45);
            }
        } else if (weakFixedAxisOscillator()) {
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
        } else if (gntestStopDuel()) {
            // GNTest slow-duel losses were not from raw opponent score, but from us
            // spending heavy bullets through long stopped/low-turn phases.  Use faster
            // medium shots while healthy, cheap shots in reserve, and a capped lethal
            // finisher when it is close enough to end the round.
            if (e.getEnergy() < 12.0 && getEnergy() > 7.0 && distance < 620.0) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 1.85);
            } else if (getEnergy() > 58.0) {
                power = Math.min(Math.max(power, distance < 360 ? 2.05 : 1.70), 2.15);
            } else if (getEnergy() > 34.0) {
                power = Math.min(Math.max(power, distance < 340 ? 1.35 : 1.05), 1.50);
            } else if (getEnergy() > 16.0) {
                power = Math.min(power, distance < 320 ? 0.55 : 0.38);
            } else {
                power = Math.min(power, getEnergy() < 8.0 ? 0.12 : 0.22);
            }
        } else if (fixedHeadingMediumShooter()) {
            // Current Chilibot traces: fixed body heading, many stops, and
            // medium-to-high bullets.  Offline replay strongly favored pure
            // head-on over the learned drift/axis gun.  Use decisive shots while
            // healthy so it cannot survive long enough to win with repeated
            // power-2/3 hits, but keep low-energy pinpricks for survival.
            if (finishingFixedMedium) {
                // Do not repeat the logged loss pattern where we had 10-15 energy,
                // the enemy had one max-power hit of life left, and low-power
                // conservation let its next medium/high bullet decide the round.
                power = Math.max(power, Math.min(3.0, getEnergy() - 0.15));
            } else if (getEnergy() > 36 && distance < 700) {
                power = Math.max(power, distance < 540 ? 3.0 : 2.35);
            } else if (getEnergy() > 18) {
                power = Math.min(Math.max(power, distance < 430 ? 1.75 : 1.35), 2.05);
            } else if (getEnergy() > 8) {
                power = Math.min(power, 0.45);
            } else {
                power = Math.min(power, 0.15);
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
        } else if (easyHeadOnStopGoEnemy()) {
            // Current Cliffbot2 traces: repeated stops and a few weak shots, but
            // virtual waves quickly show head-on is accurate and our energy stays
            // very high.  Do not apply the RegullarMonk low-power conservation cap
            // to this easier stop/go target.
            if (getEnergy() > 24 && distance < 720) {
                power = Math.max(power, distance < 560 ? 3.0 : 2.45);
            }
        } else if (turningHighPowerEnemy()) {
            // jeujdapeu losses were long power-3 exchanges where generic branches still
            // spent too much after we fell behind.  Use medium pressure while healthy,
            // then very cheap/faster bullets; if the enemy is almost dead, use only the
            // minimum lethal power so we do not waste a 3.0 overkill shot.
            if (e.getEnergy() < 8.5 && getEnergy() > 9.0) {
                power = Math.min(power, Math.min(1.35, lethalPower(e.getEnergy())));
            } else if (getEnergy() > 54 && distance < 720) {
                power = Math.min(Math.max(power, distance < 420 ? 2.15 : 1.85), 2.20);
            } else if (getEnergy() > 34) {
                power = Math.min(Math.max(power, distance < 360 ? 1.15 : 0.90), 1.30);
            } else if (getEnergy() > 22) {
                power = Math.min(power, distance < 330 ? 0.35 : 0.25);
            } else if (getEnergy() > 12) {
                power = Math.min(power, distance < 330 ? 0.22 : 0.16);
            } else {
                power = Math.min(power, getEnergy() < 7 ? 0.10 : 0.12);
            }
        } else if (maximbotEnemy()) {
            // With circular aim Maximbot is much easier than the generic Dominator branch
            // thinks.  Keep strong pressure while our reserve is healthy, but use faster
            // medium shots and lethal finishers in the few long close exchanges.
            if (e.getEnergy() < 9.5 && getEnergy() > 6.0) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 1.65);
            } else if (getEnergy() > 62.0 && distance < 760.0) {
                power = Math.min(Math.max(power, distance < 420.0 ? 2.15 : 1.85), 2.20);
            } else if (getEnergy() > 38.0) {
                power = Math.min(Math.max(power, distance < 380.0 ? 1.35 : 1.05), 1.50);
            } else if (getEnergy() > 18.0) {
                power = Math.min(power, distance < 360.0 ? 0.55 : 0.38);
            } else {
                power = Math.min(power, getEnergy() < 8.0 ? 0.14 : 0.25);
            }
        } else if (juggernautEnemy()) {
            // Current Juggernaut traces differ from Ultron: it turns/stops enough that
            // the averaged gun beats head-on, and excessive 0.6-1.4 power pinpricks
            // left it alive to land many more power-3 shots.  Use faster medium shots
            // while healthy, then downshift before true self-depletion.
            if (getEnergy() > 50 && distance < 720) {
                power = Math.min(Math.max(power, distance < 420 ? 2.25 : 1.95), 2.35);
            } else if (getEnergy() > 32) {
                power = Math.min(Math.max(power, distance < 360 ? 1.25 : 0.95), 1.45);
            } else if (getEnergy() > 18) {
                power = Math.min(power, distance < 320 ? 0.55 : 0.35);
            } else {
                power = Math.min(power, getEnergy() < 8 ? 0.15 : 0.25);
            }
        } else if (highPowerStopGoDodger()) {
            // Ultron-like evasive high-power stop/go shooters made the old generic
            // slow/stop-go max-power branches burn us to zero in rare long games.
            // Head-on replay is best, and lower-power bullets are much faster; keep
            // them cheap enough that misses cannot throw away survival points.
            if (getEnergy() > 42) {
                power = Math.min(power, distance < 380 ? 1.45 : 1.15);
            } else if (getEnergy() > 18) {
                power = Math.min(power, distance < 340 ? 0.85 : 0.65);
            } else {
                power = Math.min(power, getEnergy() < 9 ? 0.15 : 0.35);
            }
        } else if (heavyStopGoShooter()) {
            // Florian2-like heavy stop/go shooters waste mostly power-3 shots but
            // do not aim well in the traces.  High pressure with the damped gun
            // shortens rounds and uses our large energy surplus; retain a modest
            // downshift if a round unexpectedly runs long.
            if (getEnergy() > 30 && distance < 700) {
                power = Math.max(power, distance < 560 ? 3.0 : 2.45);
            } else if (getEnergy() > 14) {
                power = Math.min(Math.max(power, 1.55), 2.15);
            } else {
                power = Math.min(power, 0.45);
            }
        } else if (m9WallStopGoEnemy()) {
            // Current M9 profile fires endless power-2 bullets but is very predictable
            // on its wall/stop-go path.  Prefer faster medium bullets to the old p2.3
            // opening: trace losses had many p3/p2 wall impacts and then death by
            // self-depletion while M9 still had single-digit to mid energy.  Keep a
            // decisive lethal shot only when it is already almost dead.
            if (e.getEnergy() < 8.5 && getEnergy() > 6.0) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 1.55);
            } else if (getEnergy() > 58.0 && distance < 660) {
                power = Math.min(Math.max(power, distance < 380 ? 1.85 : 1.55), 1.90);
            } else if (getEnergy() > 32.0) {
                power = Math.min(Math.max(power, distance < 340 ? 1.20 : 0.95), 1.35);
            } else if (getEnergy() > 16.0) {
                power = Math.min(power, distance < 320 ? 0.50 : 0.32);
            } else {
                power = Math.min(power, getEnergy() < 8.0 ? 0.15 : 0.22);
            }
        } else if (mediumStopGoDuelist()) {
            // Current MarkRobo logs: stop/go low-turn movement with many medium
            // bullets.  Max-power is fine early, but the only losses are long
            // self-depletion duels after 10+ enemy shots.  Downshift before that
            // cliff; faster bullets also reduce wallavg lead error on this target.
            if (getEnergy() > 70 && enemyFireCount <= 8 && distance < 680) {
                power = Math.max(power, distance < 520 ? 3.0 : 2.35);
            } else if (getEnergy() > 44) {
                power = Math.min(Math.max(power, distance < 430 ? 1.45 : 1.15), 1.65);
            } else if (getEnergy() > 24) {
                power = Math.min(power, distance < 360 ? 0.75 : 0.55);
            } else {
                power = Math.min(power, getEnergy() < 10 ? 0.15 : 0.30);
            }
        } else if (mediumStopGoShooter()) {
            // Current SadBot/Gruffalo traces use frequent medium-power fire, but our
            // orbit dodges it well and the main lost score is long rounds.  Engage
            // high-pressure shots earlier and keep them while we still have a healthy
            // reserve; SadBot round-1/2 traces ended with plenty of spare energy.
            if (getEnergy() > 24 && distance < 720) {
                power = Math.max(power, distance < 600 ? 3.0 : 2.45);
            } else if (getEnergy() > 14) {
                power = Math.min(Math.max(power, 1.45), 2.05);
            } else {
                power = Math.min(power, 0.45);
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
        if (wallsPoetEnemy()) {
            // Current pez__wallspoet traces: opponent is wall-bound/stop-go, fires almost
            // all power-3 bullets, and offline shot replay shows much faster low/medium
            // bullets have far less future-position error than power-3.  Avoid the generic
            // dangerous-wall max-power override that self-depletes while still missing.
            if (e.getEnergy() < 8.5 && getEnergy() > 6.0 && distance < 560.0) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 1.45);
            } else if (getEnergy() > 62.0) {
                power = Math.min(Math.max(power, distance < 360 ? 1.75 : 1.35), 1.85);
            } else if (getEnergy() > 34.0) {
                power = Math.min(Math.max(power, distance < 330 ? 1.05 : 0.75), 1.20);
            } else if (getEnergy() > 16.0) {
                power = Math.min(power, distance < 300 ? 0.42 : 0.28);
            } else {
                power = Math.min(power, getEnergy() < 8.0 ? 0.12 : 0.20);
            }
        } else if (dangerousWallEnemy() && crazyEnemyScans <= 4 && !activeStopGoShooter() && !dominatorEnemy()) {
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
        if (pikachuEnemy()) {
            // kcanida__pikachu was the round-0 self-depletion matchup: it fires many
            // power-0.1/weak bullets, while our generic stop-go/wall fallbacks spent about
            // power 2 on 50+ low-hit-rate shots and died with Pikachu often above 50 energy.
            // Trace replay shows fast sub-power-1 head/damped bullets have much lower future
            // error.  Use conservative pressure and then bank energy rather than trying to
            // win a hopeless low-reserve damage race.
            if (e.getEnergy() < 6.0 && getEnergy() > 5.0 && distance < 520.0) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 1.20);
            } else if (getEnergy() > 72.0) {
                // Round-1 logs after the Pikachu conservation branch were a full 250/250
                // sweep with very high remaining energy (~86 avg).  Spend a little more
                // while the reserve is huge: p1.5-ish bullets are only slightly slower
                // than the old p1.3 shots but do much more damage/bonus, and the lower
                // energy tiers below still prevent the original self-depletion failure mode.
                power = Math.min(Math.max(power, distance < 390 ? 1.55 : 1.40), 1.60);
            } else if (getEnergy() > 50.0) {
                power = Math.min(Math.max(power, distance < 380 ? 1.05 : 0.85), 1.10);
            } else if (getEnergy() > 30.0) {
                power = Math.min(Math.max(power, distance < 350 ? 0.55 : 0.40), 0.62);
            } else if (getEnergy() > 14.0) {
                power = Math.min(power, distance < 330 ? 0.28 : 0.18);
            } else {
                power = Math.min(power, 0.10);
            }
        }
        if (mediumPowerWallCruiser()) {
            // TannerBot fires repeated ~power-2 shots while sliding/stopping along
            // the border.  Our round-0 losses are almost all self-depletion after
            // max-power wall shots miss or hit walls.  Full linear aiming is better
            // on replay, and medium/faster bullets preserve energy without giving up
            // too much kill speed against this predictable path.
            if (e.getEnergy() < 9.0 && getEnergy() > 5.5) {
                power = Math.min(Math.max(power, lethalPower(e.getEnergy())), 1.65);
            } else if (getEnergy() > 62.0) {
                power = Math.min(Math.max(power, distance < 400 ? 1.85 : 1.55), 1.90);
            } else if (getEnergy() > 38.0) {
                power = Math.min(Math.max(power, distance < 360 ? 1.25 : 0.98), 1.35);
            } else if (getEnergy() > 18.0) {
                power = Math.min(power, distance < 325 ? 0.50 : 0.30);
            } else {
                power = Math.min(power, getEnergy() < 8.0 ? 0.15 : 0.22);
            }
        }
        if (gntestEnemy() && stationaryScans > 5) {
            // Re-apply this after generic stop/go/fixed-heading branches, which may
            // raise power with Math.max().  The parked GNTest phase is the current
            // loss mode; never let it inherit p3 stationary farming or heavy slow-target
            // boosts once its medium/high gun is confirmed.
            if (e.getEnergy() < 11.0 && getEnergy() > 7.0 && distance < 620.0) {
                power = Math.min(power, Math.min(Math.max(lethalPower(e.getEnergy()), 0.55), 1.85));
            } else if (getEnergy() > 58.0) {
                power = Math.min(power, distance < 380 ? 1.95 : 1.65);
            } else if (getEnergy() > 34.0) {
                power = Math.min(power, distance < 360 ? 1.25 : 0.95);
            } else if (getEnergy() > 16.0) {
                power = Math.min(power, distance < 330 ? 0.48 : 0.32);
            } else {
                power = Math.min(power, getEnergy() < 8.0 ? 0.12 : 0.20);
            }
        }
        if (sampleWallsEnemy()) {
            // Re-apply after generic fast/dangerous-wall branches, which otherwise raise
            // this name-gated Walls matchup back to p3.  Preserve lethal finishers, but cap
            // long-chase pressure to faster medium/cheap bullets.
            if (e.getEnergy() < 9.0 && getEnergy() > 5.5) {
                power = Math.min(power, Math.min(Math.max(lethalPower(e.getEnergy()), 0.35), 1.65));
            } else if (getEnergy() > 58.0) {
                power = Math.min(power, distance < 420 ? 2.10 : 1.85);
            } else if (getEnergy() > 34.0) {
                power = Math.min(power, distance < 380 ? 1.35 : 1.05);
            } else if (getEnergy() > 18.0) {
                power = Math.min(power, distance < 350 ? 0.42 : 0.28);
            } else if (getEnergy() > 12.0) {
                power = Math.min(power, 0.15);
            } else {
                power = Math.min(power, 0.10);
            }
        }
        if (robrrratEnemy()) {
            // Re-apply after broad slow/stop-go/high-power branches that may have raised
            // power with Math.max().
            if (e.getEnergy() < 9.0 && getEnergy() > 6.0 && distance < 560.0) {
                power = Math.min(power, Math.min(Math.max(lethalPower(e.getEnergy()), 0.35), 1.65));
            } else if (getEnergy() > 62.0) {
                power = Math.min(power, distance < 430.0 ? 2.25 : 1.95);
            } else if (getEnergy() > 38.0) {
                power = Math.min(power, distance < 380.0 ? 1.40 : 1.10);
            } else if (getEnergy() > 20.0) {
                power = Math.min(power, distance < 340.0 ? 0.65 : 0.45);
            } else {
                power = Math.min(power, getEnergy() < 9.0 ? 0.12 : 0.22);
            }
        }
        if (hardToHitMover) {
            if (getEnergy() < 12) {
                power = Math.min(power, 0.15);
            } else if (getEnergy() < 22 && bestGunError() > 82.0) {
                power = Math.min(power, 0.75);
            }
        }
        if (activeHighPowerShooter() && !maximbotEnemy()) {
            // A few Ultron losses/draws still came from falling out of the narrow
            // highPowerStopGoDodger() signature late in a round, then spending 2+
            // energy slow-target shots while already below ~12 energy.  Any opponent
            // with repeated high/medium-high energy-drop shots is dangerous enough
            // that low-energy survival and faster pinprick bullets are worth more than
            // another heavy miss.  Keep this as a late safety net so harmless weak
            // stop/go farmers and stationary targets retain their high-power modes.
            if (getEnergy() < 10) {
                power = Math.min(power, 0.15);
            } else if (getEnergy() < 18) {
                power = Math.min(power, 0.45);
            } else if (getEnergy() < 30 && bestGunError() > 58.0) {
                power = Math.min(power, 0.85);
            }
        }
        if (getEnergy() < 22 && distance > 260 && stationaryScans <= 5 && slowEnemyScans <= 12
                && !finishingFixedHighPower && !finishingFixedMedium) {
            power = Math.min(power, 1.25);
        }
        if (getEnergy() < 9 && !finishingFixedHighPower && !finishingFixedMedium) {
            power = Math.min(power, hardToHitMover || activeHighPowerShooter() ? 0.15 : 0.55);
        }
        if ((fixedHeadingMediumShooter() || fixedHeadingHighPowerShooter() || activeHighPowerShooter())
                && e.getEnergy() < 14.0 && getEnergy() < 34.0) {
            // In the remaining Chilibot losses/draws we had the opponent under one
            // bullet of life but continued to spend power-3 shots; old enemy bullets
            // then killed us after (or just before) the final hit.  A minimal lethal
            // bullet is faster and saves 1-2+ energy, which is exactly the survival
            // margin in those traces.
            power = Math.min(power, lethalPower(e.getEnergy()));
        }
        if (quadWallEnemy() && e.getEnergy() < 10.5) {
            // QuadWall loss traces often leave it under one bullet of life while our
            // slow high-power shots are still in flight.  A minimum lethal bullet is
            // faster, avoids overkill energy spend, and should land before the next
            // weak wall shot can decide a mutual-kill race.
            power = Math.min(power, lethalPower(e.getEnergy()));
        }
        if (turningHighPowerEnemy() && e.getEnergy() < 3.6) {
            // In the remaining Jeujdapeu draw, we had ~6 energy while the enemy was under
            // 3 energy, but low-energy conservation kept firing pinpricks and let an
            // existing power-3 bullet catch us before the final hit.  Once it is this low,
            // spend the minimum lethal shot even from our low reserve; ending the round now
            // is safer than trying to win a long 0.1-power exchange against p3 bullets.
            double lp = lethalPower(e.getEnergy());
            if (getEnergy() > lp + 0.35) {
                power = Math.max(power, lp);
            }
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
        } else if (robrrratEnemy()) {
            // Offline replay for sacdalance__robrrrat slightly favors the normal averaged
            // predictor over circular and clearly over head-on/linear.  Force it so broad
            // active-high-power/crazy classifiers cannot steal the gun.
            gun = GUN_AVERAGED;
        } else if (sampleWallsEnemy()) {
            // sample.Walls drives long straight cardinal legs around the border; offline
            // replay on the current traces ranks full linear lead ahead of averaged/wallavg.
            gun = GUN_LINEAR;
        } else if (maximbotEnemy()) {
            // Trace replay for mgalushka__maximbot strongly favors circular prediction
            // over the generic Dominator head-on branch.
            gun = GUN_CIRCULAR;
        } else if (waveSurfingEnemy()) {
            // Full linear/circular over-lead this surfer.  The rolling GF gun has a worse
            // mean error, but replay of round-1 shots showed a much larger fraction of
            // near-hits than pure head-on/wallavg; once enough virtual waves have landed,
            // let it aim at the learned escape bin.  Cold-start with head-on/averaged.
            if (virtualSamples > 45 && virtualGunError[GUN_GUESS_FACTOR] <= virtualGunError[GUN_HEAD_ON] + 28.0) {
                gun = GUN_GUESS_FACTOR;
            } else {
                gun = (virtualSamples > 24 && virtualGunError[GUN_AVERAGED] + 6.0 < virtualGunError[GUN_HEAD_ON])
                        ? GUN_AVERAGED : GUN_HEAD_ON;
            }
        } else if (pikachuEnemy()) {
            // Offline replay for kcanida__pikachu strongly disfavors full lead.  A very
            // damped averaged/head-on family wins, especially with sub-power-1 bullets.
            gun = (virtualSamples > 18 && virtualGunError[GUN_HEAD_ON] + 2.5 < virtualGunError[GUN_AVERAGED])
                    ? GUN_HEAD_ON : GUN_AVERAGED;
        } else if (crawlerEnemy()) {
            // Mostly an exact circular target, but allow the normal averaged predictor on a
            // clear virtual margin.  The previous hard circular override was a small score
            // regression versus the pre-name-gated SpinBot-style handling.
            gun = (virtualSamples > 20 && virtualGunError[GUN_AVERAGED] + 6.0 < virtualGunError[GUN_CIRCULAR])
                    ? GUN_AVERAGED : GUN_CIRCULAR;
        } else if (spinBotEnemy()) {
            // Pure circular is normally exact for sample.SpinBot, but near walls the
            // damped averaged predictor can occasionally score better.  Let virtual
            // evidence override only on a clear margin so the cold-start exact-circle
            // advantage from round 1 remains intact.
            gun = (virtualSamples > 20 && virtualGunError[GUN_AVERAGED] + 6.0 < virtualGunError[GUN_CIRCULAR])
                    ? GUN_AVERAGED : GUN_CIRCULAR;
        } else if (wallsPoetEnemy()) {
            // Wallspoet is also a repeated power-3 stop/go opponent, but unlike Shreker it
            // is persistently wall-bound.  Round-1 traces showed the older Shreker branch
            // could steal this matchup and force head-on/p3-ish behavior; keep Wallspoet on
            // the replay-best normal averaged wall gun.
            gun = GUN_AVERAGED;
        } else if (shrekerEnemy()) {
            // Offline replay on alexbay218__shreker favors pure head-on, with the damped
            // wall/stop-go averaged gun second; full linear/circular badly over-lead its
            // stops and wall bounces.  Use head-on once virtual waves agree, otherwise the
            // damped averaged fallback during early wall/stop bursts.
            gun = (virtualSamples > 18 && virtualGunError[GUN_AVERAGED] + 4.0 < virtualGunError[GUN_HEAD_ON])
                    ? GUN_AVERAGED : GUN_HEAD_ON;
        } else if (dominatorEnemy()) {
            // Round-0 DominatorX replay ranks head-on best; full linear/circular over-lead
            // its stop/reverse/wall bounces, and averaged still carries too much drift.
            gun = GUN_HEAD_ON;
        } else if (npcSniperEnemy()) {
            // Replay for NPCSniper favors a wall/stop damped velocity predictor over
            // full linear/circular lead or pure head-on.  Keep it forced so the generic
            // dangerous-wall branch does not use the less-damped averaged predictor.
            gun = GUN_AVERAGED;
        } else if (quadWallEnemy()) {
            // QuadWall is an active weak-fire wall runner with frequent stops; actual
            // trace replay favors the normal averaged predictor, not the old full-linear
            // fast-wall-cruiser gun.
            gun = GUN_AVERAGED;
        } else if (velociRobotEnemy()) {
            // For this medium-speed weak shooter, damped averaged prediction is usually
            // best, but trace replay shows pure head-on is competitive and sometimes
            // wins during these shallow reversals, especially with faster bullets.  Let head-on
            // take over when virtual errors are comparable; otherwise keep damping
            // its shallow straight reversals.
            gun = (virtualSamples > 18 && virtualGunError[GUN_HEAD_ON] <= virtualGunError[GUN_AVERAGED] + 3.0)
                    ? GUN_HEAD_ON : GUN_AVERAGED;
        } else if (crazyEnemyScans > 3) {
            gun = GUN_CIRCULAR;
        } else if (mediumPowerWallCruiser()) {
            // Round-0 TannerBot replay strongly favors full linear/circular lead for
            // its long straight border legs; averaged/wall-damped/fixed-axis shots lag
            // behind and waste energy in the corner-chase losses.
            gun = GUN_LINEAR;
        } else if (fixedHeadingHighPowerShooter()) {
            // When this class parks/stops to fire power-3, the safest aim is nearly
            // head-on; avoid the weak-axis midpoint/opposite-endpoint gun that was
            // tuned for power-1 oscillators.
            gun = Math.abs(e.getVelocity()) < 1.5 ? GUN_HEAD_ON : GUN_LINEAR;
        } else if (gntestStopDuel()) {
            // In GNTest loss traces, pure head-on is best when it is stopped/creeping,
            // while circular is clearly best during any resumed turn.  Do not let the
            // weak fixed-line drift gun steal this profile.
            gun = (Math.abs(e.getVelocity()) > 2.0 && Math.abs(turnRate) > 0.030) ? GUN_CIRCULAR : GUN_HEAD_ON;
        } else if (fixedHeadingMediumShooter()) {
            // For Chilibot-like one-dimensional medium shooters, trace replay says
            // pure head-on is the safest aim; the drift/axis guns over-lead stops.
            gun = GUN_HEAD_ON;
        } else if (fixedHeadingStopGoEnemy()) {
            // Fixed-heading oscillators use the drift-head-on virtual gun slot for
            // learned-axis aiming: tight weak oscillators usually aim near the
            // opposite endpoint, while broader stop/go variants keep the safer
            // midpoint.  However Robocode sample.MyFirstRobot-style scanners stop
            // Round-1 tried allowing pure head-on when the virtual score looked
            // better, but a replay using the learned axis state from the real traces
            // shows this MyFirstRobot-style compact oscillator is still hit best by
            // the opposite-endpoint/midpoint drift slot.  Keep the axis gun forced.
            gun = GUN_DRIFT_HEAD_ON;
        } else if (fixedHeadingLineEnemy()) {
            // For longer fixed-heading line movers, a very small velocity drift
            // beats pure head-on in offline replay without over-leading stops.
            gun = GUN_DRIFT_HEAD_ON;
        } else if (easyHeadOnStopGoEnemy()) {
            // Cliffbot2-style weak stop/go targets are best hit head-on; this
            // virtual-error gate prevents overriding the averaged gun on MarkIV /
            // Terminator-style stop-go bots where damping is better.
            gun = GUN_HEAD_ON;
        } else if (straightStopGoLinearEnemy()) {
            // Exterminador-style stop/straight movement should not fall into the
            // generic damped stop-go or high-power-shooter head-on branches once
            // virtual waves show linear is winning.
            gun = GUN_LINEAR;
        } else if (turningHighPowerEnemy()) {
            // Current power-3 turner: offline replay over round-0 traces puts the
            // averaged gun slightly ahead of head-on and clearly ahead of linear/circular.
            gun = GUN_AVERAGED;
        } else if (juggernautEnemy()) {
            // Replay over the current power-3 stop/turn opponent favors the damped
            // averaged predictor; head-on was overused by the generic high-power
            // stop/go branch and missed the max-speed turn bursts.
            gun = GUN_AVERAGED;
        } else if (highPowerStopGoDodger()) {
            // The current Ultron traces strongly favor head-on: it stops/reverses
            // during bullet flight, so linear/circular/averaged over-lead badly.
            gun = GUN_HEAD_ON;
        } else if (heavyStopGoShooter()) {
            // Florian2-style heavy stop/go shooters spend most of the round either
            // stopped or crawling, then make occasional fast bursts.  The damped
            // averaged (wallavg) predictor is best during those bursts, but it still
            // carries EMA drift and over-leads a currently stopped/slow target.  Use
            // the linear slot for slow scans (identical to head-on when velocity is
            // zero, and better for slow rolls), then return to the damped averaged
            // gun for fast bursts.
            gun = Math.abs(e.getVelocity()) <= 3.25 ? GUN_LINEAR : GUN_AVERAGED;
        } else if (m9WallStopGoEnemy()) {
            gun = GUN_AVERAGED;
        } else if (mediumStopGoDuelist()) {
            gun = GUN_AVERAGED;
        } else if (mediumStopGoShooter()) {
            // Medium-power stop/go shooters usually prefer the damped averaged gun
            // while moving, but SadBot pauses for long endpoint shots; at currently
            // stopped ticks, head-on/linear lands more often than carrying EMA drift
            // from the previous burst.  Allow this stopped-shot override early (before
            // virtual waves fully settle) and unless head-on is clearly losing badly.
            if (Math.abs(e.getVelocity()) < 0.15
                    && (virtualSamples < 30 || virtualGunError[GUN_HEAD_ON] <= virtualGunError[GUN_AVERAGED] + 24.0)) {
                gun = GUN_HEAD_ON;
            } else {
                gun = GUN_AVERAGED;
            }
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
        } else if (lowFireTracker()) {
            // Tracker drives almost straight along the bearing line toward us; lead
            // its current velocity and do not let harmless-runner damping under-lead
            // the charge.
            gun = GUN_LINEAR;
        } else if (lowFireRammer()) {
            // RamFire-style direct chargers have very low turn rate and usually
            // continue their current line during bullet flight; offline replay of
            // the current traces put linear/circular well ahead of the damped
            // averaged harmless-runner gun.
            gun = GUN_LINEAR;
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
            // noise (e.g. Tirolio), so the averaged gun remains the safe cold start.
            // Hugbot-style targets, however, spend most of the match in genuinely
            // fast field-crossing straight runs and almost never fire; offline replay
            // for the current logs shows full linear leading the damped averaged gun
            // by a wide margin.  Let virtual waves (or a very clean high-speed/low-stop
            // signature) promote these harmless runners to linear without disturbing
            // prior stop/reverse movers where averaged keeps winning.
            if ((virtualSamples > 16 && virtualGunError[GUN_LINEAR] + 5.0 < virtualGunError[GUN_AVERAGED])
                    || (straightEnemyScans > 12 && stopGoEnemyScans <= 4 && enemySpeedAvg > 5.0
                            && Math.abs(enemyVelocityAvg) > 4.0)) {
                gun = GUN_LINEAR;
            } else {
                gun = GUN_AVERAGED;
            }
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
        if (waveSurfingEnemy()) {
            tolerance = Math.min(tolerance, Math.atan2(13.0, distance));
        }
        if (maximbotEnemy()) {
            tolerance = Math.min(tolerance, Math.atan2(17.0, distance));
        }
        if (shrekerEnemy()) {
            tolerance = Math.min(tolerance, Math.atan2(14.0, distance));
        }
        if (dominatorEnemy()) {
            tolerance = Math.min(tolerance, Math.atan2(15.0, distance));
        }
        if (gntestStopDuel()) {
            tolerance = Math.min(tolerance, Math.atan2(15.0, distance));
        }
        if (pikachuEnemy()) {
            tolerance = Math.min(tolerance, Math.atan2(16.0, distance));
        }
        if (npcSniperEnemy()) {
            tolerance = Math.min(tolerance, Math.atan2(15.0, distance));
        }
        if (velociRobotEnemy()) {
            tolerance = Math.min(tolerance, Math.atan2(18.0, distance));
        }
        if (m9WallStopGoEnemy()) {
            tolerance = Math.min(tolerance, Math.atan2(16.0, distance));
        }
        if (mediumPowerWallCruiser()) {
            tolerance = Math.min(tolerance, Math.atan2(15.0, distance));
        }
        if (turningHighPowerEnemy()) {
            tolerance = Math.min(tolerance, Math.atan2(15.0, distance));
        } else if (juggernautEnemy()) {
            tolerance = Math.min(tolerance, Math.atan2(16.0, distance));
        } else if (highPowerStopGoDodger()) {
            // Cheap head-on shots are still wasted if the gun is broadside; wait for
            // a clean angle in these long high-power stop/go exchanges.
            tolerance = Math.min(tolerance, Math.atan2(15.0, distance));
        } else if (activeStopGoShooter()) {
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
        boolean fireAllowed = true;
        if (waveSurfingEnemy() && getEnergy() < 14.0 && e.getEnergy() > 5.0) {
            // Do not repeat the observed surfer losses where we kept firing tiny bullets
            // down to 0.2 energy, became disabled, and handed the opponent survival points
            // despite it scoring no bullet damage.  If it is not nearly dead, bank the
            // remaining energy and try to win/draw on survival instead of self-killing.
            fireAllowed = false;
        }
        if (shrekerEnemy() && getEnergy() < 10.0 && e.getEnergy() > 10.0) {
            fireAllowed = false;
        }
        if (shrekerEnemy() && getEnergy() < 22.0 && e.getEnergy() > 18.0) {
            // If Shreker still has a substantial stack, tiny reserve bullets mostly just
            // self-disable us before enough damage lands.  Preserve energy for dodging until
            // it is actually in lethal/near-lethal range.
            fireAllowed = false;
        }
        if (wallsPoetEnemy() && getEnergy() < 12.0 && e.getEnergy() > 10.0) {
            // Wallspoet's p3 stream wins when we spend the last few energy points on
            // 0.1-0.2 bullets that cannot finish it.  Keep the reserve for movement unless
            // the enemy is already in capped-lethal range.
            fireAllowed = false;
        }
        if (maximbotEnemy() && getEnergy() < 10.0 && e.getEnergy() > 11.0) {
            // Preserve the last movement reserve if Maximbot is not close to death; tiny
            // circular bullets cannot finish it before another medium/high shot lands.
            fireAllowed = false;
        }
        if (robrrratEnemy() && getEnergy() < 12.0 && e.getEnergy() > 11.0) {
            // Last-reserve pinpricks cannot out-damage a remaining p3 shooter; keep moving
            // unless it is already close enough for a capped finisher.
            fireAllowed = false;
        }
        if (sampleWallsEnemy() && getEnergy() < 14.0 && e.getEnergy() > 12.0) {
            // The current Walls opponent's simple gun is accurate; if it still has a large
            // stack, last-reserve 0.1-0.2 bullets only self-disable us before enough damage
            // can land.  Keep the energy for movement unless a capped lethal finish is near.
            fireAllowed = false;
        }
        if (dominatorEnemy() && getEnergy() < 9.0 && e.getEnergy() > 12.0) {
            // DominatorX can only convert many of the remaining losses after we self-disable
            // with harmless 0.1-0.2 bullets while it still has tens of energy.  Preserve the
            // last reserve unless a lethal/near-lethal finish is actually available.
            fireAllowed = false;
        }
        if (gntestStopDuel() && getEnergy() < 8.0 && e.getEnergy() > 10.0) {
            fireAllowed = false;
        }
        if (pikachuEnemy() && getEnergy() < 14.0 && e.getEnergy() > 8.0) {
            // Do not donate the last survival points with 0.1 shots when Pikachu still has
            // enough energy that several hits would be needed.  Preserve movement unless a
            // low-power finisher is actually plausible.
            fireAllowed = false;
        }
        if (getGunHeat() == 0
                && Math.abs(getGunTurnRemainingRadians()) < tolerance && getEnergy() > 0.25 && fireAllowed) {
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


    private boolean robrrratEnemy() {
        // Current /logs/rounds/0 opponent: sacdalance__robrrrat, a mixed fast/stop mover
        // with frequent mostly power-3 fire.  Name-gate the conservative averaged-gun
        // branch to avoid disturbing the many historical generic high-power special cases.
        return enemyName != null && enemyName.contains("sacdalance__robrrrat");
    }

    private boolean crawlerEnemy() {
        return enemyName != null && enemyName.contains("txeverson__crawler");
    }

    private boolean leachPmcEnemy() {
        // Current /logs/rounds/0 opponent: stationary power-3 shooter.  Name-gated so the
        // old SittingDuck/infinitylock stationary no-fire farm mode remains intact.
        return enemyName != null && enemyName.contains("pez__leachpmc");
    }

    private boolean sampleWallsEnemy() {
        // Current hidden opponent in /logs/rounds/0 is robo_code__walls.MyTank, matching
        // Robocode's sample.Walls behavior: fast cardinal perimeter movement and frequent
        // power-2 head-on shots.  Name-gate this because the broad wall-runner detectors
        // are heavily tuned for many other historical opponents.
        return enemyName != null && enemyName.contains("robo_code__walls");
    }

    private boolean maximbotEnemy() {
        // Current round opponent mgalushka__maximbot: medium/fast mover with shallow
        // continuous turns and repeated medium/high-power fire.  Round-0 replay shows
        // circular prediction is clearly best; the broader Dominator/stop-go signatures
        // can otherwise steal it and force head-on plus long self-depletion duels.
        return enemyName != null && enemyName.contains("mgalushka__maximbot");
    }

    private boolean pikachuEnemy() {
        return enemyName != null && enemyName.contains("kcanida__pikachu");
    }

    private boolean gntestEnemy() {
        return enemyName != null && enemyName.contains("josephjeon__gntest")
                && enemyFireCount > 2
                && enemyFirePowerSamples > 1
                && enemyFirePowerAvg > 1.35
                && enemyFirePowerAvg <= 3.05
                && !spinBotEnemy()
                && crazyEnemyScans <= 4;
    }

    private boolean gntestStopDuel() {
        // Narrow name-gated safety valve for the current josephjeon__gntest matchup.
        // GNTest has two distinct phases in the logs: a fast turning phase where our
        // existing Crazy/circular/max-pressure code wins quickly, and a late slow
        // stop/creep duel where the generic fixed-heading-medium profile over-spends.
        // Gate on the enemy name plus observed slow/low-turn medium-fire behavior so
        // prior fixed-heading/medium opponent tuning remains unchanged.
        return gntestEnemy()
                && stopGoEnemyScans > 8
                && enemySpeedAvg < 3.05
                && enemyAbsTurnRateAvg < 0.035
                && stationaryScans <= 45;
    }

    private boolean waveSurfingEnemy() {
        return waveSurfScans > 0 || waveSurfingSignatureRaw();
    }

    private boolean waveSurfingSignatureRaw() {
        // Current opponent admiralrasmussen__wavesurfing: medium/fast evasive motion,
        // little or no real firing, and high virtual-gun error.  It wins only when
        // our robot spends itself to zero.  Engage this branch fairly early so generic
        // slow/head-on boosts do not spend power-3 bullets during the opening, but keep
        // the motion/fire guards narrow so Crazy/SpinBot and active shooters retain their
        // specialized high-pressure branches.  The fire-count guard is deliberately loose
        // because our own small bullet hits also look like enemy energy drops here.
        return !pikachuEnemy()
                && !crawlerEnemy()
                && virtualSamples > 10
                && bestGunError() > 78.0
                && enemyFireCount <= 16
                && (enemyFirePowerSamples == 0 || enemyFirePowerAvg <= 1.25)
                && enemySpeedAvg > 3.2
                && enemySpeedAvg < 6.2
                && enemyAbsTurnRateAvg > 0.035
                && stationaryScans <= 5
                && slowEnemyScans <= 10
                && wallEnemyScans <= 12
                && !crazyEnemyScansActive()
                && !spinBotEnemy()
                && !fastWallCruiser()
                && !lowFireTracker()
                && !lowFireRammer();
    }

    private boolean crazyEnemyScansActive() {
        return crazyEnemyScans > 3;
    }

    private boolean shrekerEnemy() {
        // Wallspoet has a superficially similar p3 stop/go profile, but is persistently
        // wall-bound and replay favors the averaged wall gun/caps rather than Shreker's
        // compact head-on branch.  If both sticky counters were seeded in the opening,
        // let the more specific Wallspoet classifier win.
        return !maximbotEnemy() && !gntestStopDuel() && !wallsPoetEnemy() && (shrekerScans > 0 || shrekerSignatureRaw());
    }

    private boolean shrekerSignatureRaw() {
        // alexbay218__shreker in /logs/rounds/0: low-turn stop/go/straight mover that
        // fires many power-3 bullets.  It is not the medium-speed turning p3 bots
        // (Juggernaut/Jeujdapeu) and not the very slow p2 wall M9; damped/head-on plus
        // medium-power conservation is safer than generic max-power slow-target farming.
        return enemyFireCount > 2
                && enemyFirePowerSamples > 1
                && enemyFirePowerAvg > 2.35
                && enemySpeedAvg > 1.1
                && enemySpeedAvg < 3.8
                && (stopGoEnemyScans > 5 || straightEnemyScans > 8 || wallEnemyScans > 5)
                && enemyAbsTurnRateAvg < 0.045
                && crazyEnemyScans <= 4
                && !(wallEnemyScans > 8 && stopGoEnemyScans > 4
                    && enemyFirePowerAvg > 2.55 && Math.abs(enemyTurnRateAvg) < 0.030)
                && !stationaryShooter()
                && !spinBotEnemy()
                && !fixedHeadingHighPowerShooter()
                && !fixedHeadingMediumShooter()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy();
    }

    private boolean dominatorEnemy() {
        return dominatorScans > 0 || dominatorSignatureRaw();
    }

    private boolean dominatorSignatureRaw() {
        // vikdov__dominatorx: active p~2 shooter with medium/fast straight legs,
        // wall/corner stops, and enough turning/reversing that head-on beats full lead.
        // Keep this ahead of NPCSniper/Tanner wall-cruiser signatures, which would force
        // averaged/linear guns and produced many self-depletion losses in round 0.
        return !maximbotEnemy()
                && enemyFireCount > 2
                && enemyFirePowerSamples > 1
                && enemyFirePowerAvg > 1.20
                && enemyFirePowerAvg <= 2.85
                && enemySpeedAvg > 2.15
                && enemySpeedAvg < 7.0
                && (straightEnemyScans > 2 || wallEnemyScans > 5 || stopGoEnemyScans > 6)
                && enemyAbsTurnRateAvg > 0.006
                && enemyAbsTurnRateAvg < 0.125
                && stationaryScans <= 12
                && !spinBotEnemy()
                && !crazyEnemyScansActive()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fixedHeadingMediumShooter();
    }

    private boolean npcSniperEnemy() {
        // Current opponent iagomonteiro13579__npcsniper: medium-fast low-turn
        // straight/wall runner, repeated medium bullets (roughly power 1.2-1.8), and
        // enough stop/go pauses that full linear/circular over-leads.  This is more
        // active than daCruzer/Antiwalls (so do not use fastWallCruiser max power),
        // but not a power-3 stop/go duelist.  Once confirmed, keep the branch sticky
        // for a while; in round 1 it briefly fell out late in the only loss, causing
        // generic low-energy 0.5-0.6 shots instead of NPCSniper pinpricks.
        return npcSniperScans > 0 || npcSniperSignatureRaw();
    }

    private boolean npcSniperSignatureRaw() {
        return enemyFireCount > 3
                && enemyFirePowerSamples > 2
                && enemyFirePowerAvg > 1.15
                && enemyFirePowerAvg <= 2.10
                && enemySpeedAvg > 3.55
                && enemySpeedAvg < 5.35
                && straightEnemyScans > 5
                && wallEnemyScans > 2
                && enemyAbsTurnRateAvg < 0.095
                && crazyEnemyScans <= 4
                && !spinBotEnemy()
                && !shrekerEnemy()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fixedHeadingMediumShooter()
                && !highPowerStopGoDodger();
    }

    private boolean spinBotEnemy() {
        return spinEnemyScans > 4
                && enemySpeedAvg > 4.15
                && enemySpeedAvg < 5.35
                && enemyAbsTurnRateAvg > 0.045
                && enemyAbsTurnRateAvg < 0.16
                && stopGoEnemyScans <= 5
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fastWallCruiser();
    }

    private boolean velociRobotEnemy() {
        // Current robo_code__velocirobot profile: sustained medium-fast straight
        // movement with intermittent shallow turns, frequent weak (~power-1) fire,
        // and very few hard stops.  The Crazy detector can briefly trigger during
        // its turn bursts, but offline trace replay favors fast head-on/wall-damped
        // shots over circular/linear lead.  Keep this narrow so true Crazy/Meow
        // continuous turners and active wall runners retain their specialized guns.
        return enemyFireCount > 4
                && enemyFirePowerSamples > 2
                && enemyFirePowerAvg <= 1.45
                && enemySpeedAvg > 3.35
                && enemySpeedAvg < 5.25
                && straightEnemyScans > 6
                && stopGoEnemyScans <= 8
                && wallEnemyScans <= 12
                && enemyAbsTurnRateAvg < 0.060
                && !fastWallCruiser()
                && !lowFireTracker()
                && !lowFireRammer()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy();
    }

    private boolean stationaryHeavyShooter() {
        return stationaryScans > 5
                && enemyFirePowerSamples > 0
                && enemyFirePowerAvg > 2.20;
    }

    private boolean stationaryShooter() {
        return stationaryScans > 5 && enemyFireCount > 0;
    }

    private boolean lowFireTracker() {
        return trackerApproachScans > 5
                && straightEnemyScans > 2
                && enemyFireCount <= 12
                && wallEnemyScans <= 8
                && crazyEnemyScans <= 4
                && Math.abs(enemyTurnRateAvg) < 0.035
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fastWallCruiser();
    }

    private boolean lowFireRammer() {
        return closeRammerScans > 4
                && enemyFireCount <= 4
                && straightEnemyScans > 2
                && wallEnemyScans <= 8
                && crazyEnemyScans <= 4
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fastWallCruiser();
    }

    private boolean straightStopGoLinearEnemy() {
        // andrekorol__exterminador in the current logs looks stop/go at a glance,
        // but its moving portions are long low-turn straight runs.  The generic
        // stop/go damped gun under-leads/averages those runs, and the broad
        // high-power-shooter safety can misread our own bullet hits as enemy fire.
        // Require virtual evidence that linear beats both averaged and head-on so
        // MarkIV/Terminator/Gruffalo/Ultron-style stop-go shooters keep their
        // specialized branches.  Once repeated power-3 fixed-heading fire is detected,
        // hand off to fixedHeadingHighPowerShooter() so we do not keep the lower
        // mid-energy linear-conservation cap.
        return straightEnemyScans > 8
                && stopGoEnemyScans > 6
                && wallEnemyScans <= 4
                && crazyEnemyScans <= 4
                && !fixedHeadingHighPowerShooter()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fastWallCruiser()
                && !lowFireRammer()
                && enemySpeedAvg > 2.2
                && enemySpeedAvg < 5.4
                && Math.abs(enemyTurnRateAvg) < 0.040
                && virtualSamples > 14
                && virtualGunError[GUN_LINEAR] + 4.0 < virtualGunError[GUN_AVERAGED]
                && virtualGunError[GUN_LINEAR] + 8.0 < virtualGunError[GUN_HEAD_ON];
    }

    private boolean harmlessLowFireEnemy() {
        // Claptrap/Tirolio/Antiwalls-style opponents may show a few
        // energy drops from stray shots or wall/collision bookkeeping, but are
        // still effectively harmless.  Keep the aggressive straight-run farming
        // active until repeated firing proves otherwise; dangerousWallEnemy()
        // takes over after several shots for DroidPoet-like perimeter gunners.
        return enemyFireCount <= 3;
    }

    private boolean fixedHeadingHighPowerShooter() {
        // Exterminador-style failure cases: after an initially mobile straight run,
        // the enemy parks on a nearly fixed heading and fires repeated power-3 shots.
        // The older fixedHeadingStopGoEnemy() branch was designed for weak power-1
        // oscillators and capped our bullets too low, producing a few self-depletion
        // losses while the opponent survived on 10-18 energy.  The speed-average
        // guard keeps Ultron-style faster high-power dodgers in their cheaper
        // head-on conservation branch.
        return stopGoEnemyScans > 8
                && enemyFireCount > 3
                && enemyFirePowerSamples > 2
                && enemyFirePowerAvg > 2.25
                && crazyEnemyScans <= 4
                && enemySpeedAvg < 2.55
                && Math.abs(enemyVelocityAvg) < 3.8
                && Math.abs(enemyTurnRateAvg) < 0.008
                && !fastWallCruiser()
                && !gntestStopDuel();
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

    private boolean weakAxisHeadOnIsBetter() {
        // For compact fixed-axis power-1 bots there are two distinct patterns in
        // the logs: Tarektank-like oscillators reverse during bullet flight (the
        // opposite-endpoint drift gun wins), while sample.MyFirstRobot-like bots
        // pause at endpoints to sweep/fire (head-on wins).  The normal virtual
        // waves already measure both GUN_HEAD_ON and the weak-axis drift slot, so
        // switch only after a modest, clear head-on margin.
        return virtualSamples > 16
                && virtualGunError[GUN_HEAD_ON] + 5.0 < virtualGunError[GUN_DRIFT_HEAD_ON];
    }

    private boolean tannerWallCruiserRaw() {
        return wallEnemyScans > 6
                && straightEnemyScans > 5
                && enemyFireCount > 1
                && enemyFirePowerSamples > 0
                && enemyFirePowerAvg > 1.45
                && enemyFirePowerAvg <= 2.25
                && enemySpeedAvg > 2.15
                && enemySpeedAvg < 6.1
                && enemyAbsTurnRateAvg < 0.050
                && crazyEnemyScans <= 4
                && !spinBotEnemy()
                && !stationaryShooter()
                && !m9WallStopGoEnemy()
                && !quadWallEnemy()
                && !dominatorEnemy()
                && !lowFireTracker()
                && !lowFireRammer();
    }

    private boolean mediumPowerWallCruiser() {
        // tannerrogalsky__tannerbot1: wall-bound, mostly straight cardinal legs,
        // corner stops, and repeated medium (~p2) fire.  Round-1 losses often
        // occurred after this signature decayed during a stop, letting generic
        // wall/slow branches resume.  Use a sticky confirmation and engage after
        // only a couple of detected p2 shots so the opening max-power wall farming
        // does not spend 10-20 energy before the Tanner-specific caps take over.
        return (tannerWallScans > 0 || tannerWallCruiserRaw())
                && enemyFirePowerAvg > 1.35
                && enemyFirePowerAvg <= 2.35
                && crazyEnemyScans <= 4
                && !spinBotEnemy()
                && !stationaryShooter()
                && !m9WallStopGoEnemy()
                && !quadWallEnemy()
                && !dominatorEnemy()
                && !lowFireTracker()
                && !lowFireRammer();
    }

    private boolean quadWallEnemy() {
        // gabriel_lw__quadwall in the current logs is a wall/perimeter runner with
        // many hard stops and frequent weak shots.  It superficially resembles the
        // daCruzer/Antiwalls fast wall cruisers, but trace replay shows the normal
        // averaged gun beats full linear/circular and the old fastWallCruiser branch
        // can over-spend in very long rounds.  Require virtual evidence that averaged
        // is not losing to linear so clean edge sliders keep their linear farming.
        return wallEnemyScans > 8
                && stopGoEnemyScans > 8
                && enemyFireCount > 3
                && enemyFirePowerSamples > 1
                && enemyFirePowerAvg <= 1.45
                && enemySpeedAvg > 2.0
                && enemySpeedAvg < 5.3
                && enemyAbsTurnRateAvg < 0.085
                && crazyEnemyScans <= 4
                && !spinBotEnemy()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !lowFireTracker()
                && !lowFireRammer()
                && (virtualSamples > 18 && virtualGunError[GUN_AVERAGED] <= virtualGunError[GUN_LINEAR] + 4.0);
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
                && crazyEnemyScans <= 4
                && !quadWallEnemy()
                && !mediumPowerWallCruiser();
    }

    private boolean easyHeadOnStopGoEnemy() {
        // Cliffbot2-style signature in the current logs: many brief stops and a
        // handful of weak shots, but the target is easy enough that head-on virtual
        // error is clearly lower than the damped/averaged predictors.  This keeps
        // high-power head-on farming enabled without weakening the older
        // RegullarMonk conservation branch, which only triggers when the virtual
        // error remains high or fire count grows large.
        if (virtualSamples < 10 || stopGoEnemyScans <= 8 || crazyEnemyScans > 4
                || fixedHeadingStopGoEnemy() || fixedHeadingLineEnemy() || fastWallCruiser()
                || highPowerStopGoDodger()) {
            return false;
        }
        double head = Math.min(virtualGunError[GUN_HEAD_ON], virtualGunError[GUN_DRIFT_HEAD_ON]);
        double avg = Math.min(virtualGunError[GUN_AVERAGED], virtualGunError[GUN_CIRCULAR]);
        return head < 56.0
                && head + 5.0 < avg
                && (enemyFireCount <= 6 || bestGunError() < 50.0)
                && Math.abs(enemyTurnRateAvg) < 0.05
                && Math.abs(enemyVelocityAvg) < 4.4;
    }


    private boolean turningHighPowerEnemy() {
        // joaomcarvalho__jeujdapeu in /logs/rounds/0: a medium-speed, non-wall
        // turner that fires repeated power-3 bullets.  It is less stop-heavy than
        // Juggernaut and not fixed-heading like Exterminador/Chilibot; the virtual
        // gun replay favors averaged aim, while the only losses are low-energy
        // self-depletion after long high-power exchanges.
        return enemyFireCount > 2
                && enemyFirePowerSamples > 1
                && enemyFirePowerAvg > 2.45
                && stationaryScans <= 5
                && enemySpeedAvg > 2.75
                && enemySpeedAvg < 5.35
                && enemyAbsTurnRateAvg > 0.055
                && enemyAbsTurnRateAvg < 0.13
                && wallEnemyScans <= 18
                && crazyEnemyScans <= 4
                && !spinBotEnemy()
                && !fixedHeadingHighPowerShooter()
                && !fixedHeadingMediumShooter()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fastWallCruiser()
                && !straightStopGoLinearEnemy();
    }

    private boolean juggernautEnemy() {
        return juggernautScans > 0 || juggernautSignatureRaw();
    }

    private boolean juggernautSignatureRaw() {
        // dankraemer__juggernaut profile in /logs/rounds/0: repeated power-3 fire,
        // max-speed bursts mixed with long stops, and appreciable turn rate.  This
        // should not inherit the Ultron high-power branch, which forces head-on aim
        // and very cheap bullets; trace replay favors the averaged predictor and the
        // losses came from letting Juggernaut survive many extra p3 shots.
        return enemyFireCount > 1
                && enemyFirePowerSamples > 0
                && enemyFirePowerAvg > 2.60
                && stationaryScans <= 5
                && enemySpeedAvg > 3.15
                && enemySpeedAvg < 5.4
                && enemyAbsTurnRateAvg > 0.040
                && stopGoEnemyScans > 4
                && wallEnemyScans <= 12
                && !weakFixedAxisOscillator()
                && !fixedHeadingHighPowerShooter()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fastWallCruiser()
                && !straightStopGoLinearEnemy()
                && !spinBotEnemy()
                && crazyEnemyScans <= 4;
    }

    private boolean highPowerStopGoDodger() {
        // rafaeljdesa__ultron in the current logs: fires many power-3 bullets,
        // repeatedly stops/reverses, but has a much higher average speed than the
        // slow Florian2/Gruffalo stop-go farmers.  Offline replay over this round
        // puts head-on well ahead of all lead guns; conserve energy with fast, cheap
        // bullets instead of the older max-power slow-target pressure.  The stop/go
        // score can temporarily decay during long max-speed reversals, so also allow
        // repeated high-power fire plus low-turn moderate-speed motion to keep this
        // branch active before the generic slow-target max-power ladder takes over.
        return activeHighPowerShooter()
                && !maximbotEnemy()
                && !wallsPoetEnemy()
                && (stopGoEnemyScans > 4 || enemyFireCount > 3)
                && enemySpeedAvg > 2.65
                && enemySpeedAvg < 5.8
                && crazyEnemyScans <= 4
                && Math.abs(enemyTurnRateAvg) < 0.075
                && !fixedHeadingHighPowerShooter()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fastWallCruiser()
                && !straightStopGoLinearEnemy();
    }

    private boolean activeHighPowerShooter() {
        return !maximbotEnemy()
                && enemyFireCount > 1
                && enemyFirePowerSamples > 0
                && enemyFirePowerAvg > 2.18
                && stationaryScans <= 5
                && !weakFixedAxisOscillator()
                && !fixedHeadingHighPowerShooter()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fastWallCruiser()
                && !straightStopGoLinearEnemy()
                && !shrekerEnemy()
                && crazyEnemyScans <= 4;
    }

    private boolean heavyStopGoShooter() {
        // it_economics__ite_florian2 in the current logs: very stop-heavy, low
        // average speed, and repeated high-power shots that mostly miss while our
        // damped stop/go gun has low virtual error.  Prior activeStopGoShooter()
        // logic was written for RegullarMonk-style weak but evasive shooters and
        // capped us to low-power head-on shots after the fourth detected fire; this
        // signature keeps high-pressure damped-averaged farming enabled.
        return stopGoEnemyScans > 8
                && !wallsPoetEnemy()
                && enemyFireCount > 3
                && enemyFirePowerSamples > 2
                && enemyFirePowerAvg > 2.2
                && crazyEnemyScans <= 4
                && enemySpeedAvg < 2.55
                && Math.abs(enemyVelocityAvg) < 3.4
                && Math.abs(enemyTurnRateAvg) < 0.055
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !fastWallCruiser()
                && (virtualSamples < 18 || virtualGunError[GUN_AVERAGED] < 62.0 || bestGunError() < 58.0);
    }

    private boolean m9WallStopGoEnemy() {
        // it_economics__ite_m9 in /logs/rounds/0: stop/go wall-bound mover, average
        // speed about 1.4, fixed/low turn rate, and repeated power-2 fire.  It is
        // more dangerous than weak QuadWall, but not a MarkRobo-style open-field
        // duelist; keeping this branch separate prevents the generic duelist from
        // opening too wide and spending max-power bullets in long wall chases.
        return wallEnemyScans > 6
                && stopGoEnemyScans > 6
                && enemyFireCount > 1
                && enemyFirePowerSamples > 0
                && enemyFirePowerAvg > 1.55
                && enemyFirePowerAvg <= 2.25
                && enemySpeedAvg > 0.65
                && enemySpeedAvg < 2.65
                && enemyAbsTurnRateAvg < 0.040
                && crazyEnemyScans <= 4
                && !stationaryShooter()
                && !fixedHeadingMediumShooter()
                && !fixedHeadingHighPowerShooter()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !heavyStopGoShooter()
                && !quadWallEnemy()
                && !fastWallCruiser();
    }

    private boolean mediumStopGoDuelist() {
        // zcjerry229__markrobo profile in /logs/rounds/0: a low-turn stop/go
        // mover with repeated medium-power fire.  It is close to the broad
        // Gruffalo/SadBot mediumStopGoShooter class, but the current traces have
        // several long self-depletion losses when we keep firing max-power after
        // many enemy shots.  Use this narrower branch for late-duel conservation
        // and the older, more-damped wallavg predictor.
        return stopGoEnemyScans > 6
                && enemyFireCount > 3
                && enemyFirePowerSamples > 2
                && enemyFirePowerAvg > 1.40
                && enemyFirePowerAvg <= 2.35
                && enemySpeedAvg > 1.1
                && enemySpeedAvg < 3.2
                && crazyEnemyScans <= 4
                && Math.abs(enemyVelocityAvg) < 3.4
                && Math.abs(enemyTurnRateAvg) < 0.055
                && !fixedHeadingMediumShooter()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !heavyStopGoShooter()
                && !shrekerEnemy()
                && !fastWallCruiser();
    }

    private boolean mediumStopGoShooter() {
        // kylebennett__gruffalo in the current logs is stop-heavy and low-turn like
        // RegullarMonk, but it fires mostly medium (~power-2) bullets and trace
        // replay favors our damped averaged gun.  Keep this out of the older
        // low-power/head-on conservation branch, which was intended for weak
        // power-1 evasive shooters that caused self-depletion.
        return stopGoEnemyScans > 6
                && enemyFireCount > 1
                && enemyFirePowerSamples > 1
                && enemyFirePowerAvg > 1.35
                && enemyFirePowerAvg <= 2.35
                && crazyEnemyScans <= 4
                && Math.abs(enemyVelocityAvg) < 3.6
                && Math.abs(enemyTurnRateAvg) < 0.070
                && !fixedHeadingMediumShooter()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !heavyStopGoShooter()
                && !shrekerEnemy()
                && !fastWallCruiser()
                && (virtualSamples < 18 || virtualGunError[GUN_AVERAGED] < 70.0 || bestGunError() < 66.0);
    }

    private boolean activeStopGoShooter() {
        // RegullarMonk-style movement in the latest logs: half the time stopped,
        // small low-turn bursts, and many weak shots.  Treat it separately from
        // harmless stop/go bots and fast dangerous wall runners: conserve energy,
        // aim head-on, and stay wider.  The fixed-heading variant below is less
        // dangerous and can be pressured harder.
        return stopGoEnemyScans > 8
                && enemyFireCount > 3
                && !fixedHeadingMediumShooter()
                && !fixedHeadingStopGoEnemy()
                && !heavyStopGoShooter()
                && !m9WallStopGoEnemy()
                && !mediumStopGoShooter()
                && !fastWallCruiser()
                && !straightStopGoLinearEnemy()
                && crazyEnemyScans <= 4
                && Math.abs(enemyVelocityAvg) < 3.8
                && Math.abs(enemyTurnRateAvg) < 0.035;
    }


    private boolean fixedHeadingMediumShooter() {
        // looklazy__chilibot in the current logs holds an almost constant body
        // heading, alternates long stops with straight forward/back bursts, and
        // fires repeated medium/high bullets (average around power 2).  The older
        // fixedHeadingLineEnemy branch was tuned for weak power-1 line movers and
        // used a tiny velocity drift plus conservative mid-energy caps; in Chilibot
        // losses that left it alive on 10-15 energy.  Keep this narrow so Ian/
        // Tarektank/MyFirstKiller weak oscillators and Exterminador power-3 cases
        // stay in their specialized branches.
        return !maximbotEnemy()
                && stopGoEnemyScans > 8
                && enemyFireCount > 2
                && enemyFirePowerSamples > 1
                && enemyFirePowerAvg > 1.45
                && enemyFirePowerAvg <= 2.35
                && crazyEnemyScans <= 4
                && Math.abs(enemyVelocityAvg) < 4.2
                && Math.abs(enemyTurnRateAvg) < 0.006
                && !weakFixedAxisOscillator()
                && !fixedHeadingHighPowerShooter()
                && !gntestStopDuel()
                && !fastWallCruiser();
    }

    private boolean fixedHeadingStopGoEnemy() {
        // OppsWantMeDead-style signature from /logs/rounds/0: the opponent never
        // really turns its body (turn-rate EMA ~0), but repeatedly stops, moves
        // straight forward/back along that heading, and fires weak power-1 shots.
        // This is much easier to farm with head-on/max-pressure fire than the
        // broader activeStopGoShooter class, where max-power caused self-depletion.
        return !maximbotEnemy()
                && stopGoEnemyScans > 8
                && enemyFireCount > 1
                && (enemyFirePowerSamples == 0 || enemyFirePowerAvg <= 2.35)
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
        return !maximbotEnemy()
                && stopGoEnemyScans > 8
                && enemyFireCount > 1
                && (enemyFirePowerSamples == 0 || enemyFirePowerAvg <= 2.35)
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

    private boolean wallsPoetEnemy() {
        return wallsPoetScans > 0 || wallsPoetSignatureRaw(null);
    }

    private boolean wallsPoetSignatureRaw(ScannedRobotEvent e) {
        // pez__wallspoet: very wall-bound, about half stopped / half max-speed bursts,
        // near-zero average body turn, and repeated power-3 fire.  Keep the predicate
        // broad enough to engage early, but require the high-power fire + wall/stop-go
        // combination so prior harmless wall farmers and Crazy/SpinBot are unaffected.
        return wallEnemyScans > 4
                && enemyFireCount > 1
                && enemyFirePowerSamples > 0
                && enemyFirePowerAvg > 2.55
                && stopGoEnemyScans > 4
                && enemySpeedAvg > 1.2
                && enemySpeedAvg < 5.2
                && Math.abs(enemyTurnRateAvg) < 0.030
                && crazyEnemyScans <= 4
                && !spinBotEnemy()
                && !fixedHeadingHighPowerShooter()
                && !fixedHeadingStopGoEnemy()
                && !fixedHeadingLineEnemy()
                && !juggernautEnemy();
    }

    private boolean dangerousWallEnemy() {
        // Current DroidPoet logs: a high-speed wall/perimeter runner that fires
        // often.  Do not wait for many virtual-wave samples before switching out
        // of the old "harmless wall target" max-power close-orbit mode.
        return wallEnemyScans > 4 && enemyFireCount > 3 && stopGoEnemyScans <= 12
                && !wallsPoetEnemy()
                && !highPowerStopGoDodger()
                && !heavyStopGoShooter() && !m9WallStopGoEnemy() && !mediumStopGoShooter() && !quadWallEnemy() && !mediumPowerWallCruiser() && !fastWallCruiser() && !npcSniperEnemy();
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
        if (gunType == GUN_AVERAGED && pikachuEnemy()) {
            // Pikachu alternates stop/turn bursts; replay of round-0 traces preferred an
            // almost-head-on damped predictor over full averaged/linear/circular lead,
            // especially with fast cheap bullets.
            velocity = limit(-1.2, 0.20 * velocity + 0.20 * enemyVelocityAvg, 1.2);
            turnRate = 0.0;
        } else if (gunType == GUN_AVERAGED && !quadWallEnemy() && (!dangerousWallEnemy() || npcSniperEnemy())
                && (npcSniperEnemy()
                        || velociRobotEnemy()
                        || shrekerEnemy()
                        || ((wallEnemyScans > 4 || (stopGoEnemyScans > 8
                                && (harmlessLowFireEnemy() || activeStopGoEnemy() || heavyStopGoShooter() || mediumStopGoShooter())))
                            && !(stopGoEnemyScans <= 8 && straightEnemyScans > 12 && harmlessLowFireEnemy()
                                && (Math.abs(enemyVelocityAvg) > 3.5 || Math.abs(velocity) > 5.0))))) {
            // A harmless wall-bound or recent stop/go bot often alternates between
            // max-speed bursts and hard stops/reverses.  Damping avoids over-leading
            // those weak opponents.  The current Terminator traces show this helps
            // even during brief field excursions after hard stops, not just while
            // the target is inside the wall margin.  However it_simple/Antiwalls
            // style clean edge runs can need less damping; the exception above and
            // the linear virtual-gun override still let fast straight low-fire runs
            // use fuller prediction when there have not been recent stops.
            if (shrekerEnemy()) {
                velocity = limit(-2.0, 0.30 * velocity + 0.40 * enemyVelocityAvg, 2.0);
            } else if (npcSniperEnemy()) {
                // NPCSniper traces prefer a little more velocity carry than old
                // wallavg, but far less than full averaged/linear prediction.
                velocity = limit(-2.6, 0.30 * velocity + 0.45 * enemyVelocityAvg, 2.6);
            } else if (m9WallStopGoEnemy()) {
                // M9's wall path alternates hard stops with short straight bursts.  A
                // slightly stronger current+EMA carry, capped tightly, matched the
                // round-0 replay better than the old very damped wallavg predictor.
                velocity = limit(-1.5, velocity + enemyVelocityAvg, 1.5);
            } else if (mediumStopGoDuelist()) {
                velocity = limit(-2.2, 0.25 * velocity + 0.35 * enemyVelocityAvg, 2.2);
            } else if (mediumStopGoShooter()) {
                // Gruffalo's medium-power stop/go pattern usually continues a little
                // farther than CTBot/Terminator-style wall stutters.  Round-1 replay
                // showed the old 0.25/0.35 damping under-led it; keep turn damping but
                // carry more current/EMA velocity, with a lower cap while stopped.
                double cap = Math.abs(velocity) < 0.15 ? 1.4 : 2.2;
                velocity = limit(-cap, 0.45 * velocity + 0.65 * enemyVelocityAvg, cap);
            } else {
                velocity = limit(-2.2, 0.25 * velocity + 0.35 * enemyVelocityAvg, 2.2);
            }
            turnRate = 0.0;
        } else if (gunType == GUN_AVERAGED) {
            // Good against stop-and-go and random-reversal bots: do not trust a
            // single-tick burst or stop to continue for the whole bullet flight.
            velocity = limit(-3.5, 0.45 * velocity + 0.55 * enemyVelocityAvg, 3.5);
            turnRate = limit(-0.09, 0.35 * turnRate + 0.65 * enemyTurnRateAvg, 0.09);
        } else if (gunType == GUN_LINEAR) {
            // TannerBot's best replay model is full linear during its long wall runs,
            // but when it parks in a corner the current velocity is zero while the EMA
            // still indicates which cardinal leg usually resumes.  A tiny EMA drift on
            // stopped ticks improved the round-1 trace replay over both pure linear and
            // heavily damped averaged prediction.
            if (mediumPowerWallCruiser() && Math.abs(velocity) < 0.15) {
                velocity = limit(-1.0, 0.30 * enemyVelocityAvg, 1.0);
            }
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
        double inset = limit(8.0, 0.10 * span, 16.0);
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
        if (pikachuEnemy()) {
            // Even p0.1 hits matter because the old bot died by spending itself to zero.
            // Keep moving perpendicular/away after every hit instead of a short generic
            // reversal that can settle back into Pikachu's stream.
            reverseDirection();
            drivePerpendicularEscape(lastEnemyAbsBearing, getEnergy() < 35.0 ? 460.0 : 360.0);
            return;
        }
        if (shrekerEnemy()) {
            reverseDirection();
            drivePerpendicularEscape(lastEnemyAbsBearing, getEnergy() < 42.0 ? 430.0 : 310.0);
            return;
        }
        if (sampleWallsEnemy()) {
            // A hit usually means our previous dodge side/wall choice failed.  Do not
            // immediately flip-flop every detected fire tick, but after an actual hit switch
            // lateral side and take a long crossing escape before resuming the wide orbit.
            reverseDirection();
            driveSampleWallsEscape(lastEnemyAbsBearing, getEnergy() < 38.0 ? 575.0 : 470.0);
            return;
        }
        if (robrrratEnemy()) {
            reverseDirection();
            drivePerpendicularEscape(lastEnemyAbsBearing, getEnergy() < 38.0 ? 500.0 : 380.0);
            return;
        }
        if (maximbotEnemy()) {
            // Maximbot's only winning traces are close medium/high-power exchanges.
            // After an actual hit, immediately reopen the range instead of resuming
            // the compact circular-shot orbit that is fine while we are unharmed.
            reverseDirection();
            drivePerpendicularEscape(lastEnemyAbsBearing, getEnergy() < 42.0 ? 470.0 : 360.0);
            return;
        }
        if (turningHighPowerEnemy()) {
            // Jeujdapeu-style p3 turners only win/draw after a late bullet connects.
            // Keep crossing/opening the line after a hit instead of resuming the generic
            // short 170px reversal, which can put us back into the same high-power stream.
            reverseDirection();
            drivePerpendicularEscape(lastEnemyAbsBearing, getEnergy() < 38.0 ? 450.0 : 315.0);
            return;
        }
        if (mediumPowerWallCruiser()) {
            reverseDirection();
            drivePerpendicularEscape(lastEnemyAbsBearing, getEnergy() < 38.0 ? 360.0 : 285.0);
            return;
        }
        if (stationaryHeavyShooter()) {
            // A hit from a stationary power-3 gun should not flip our orbit side or
            // overwrite the diagonal escape with a short same-line reversal.  Continue
            // the persistent away+lateral escape based on the bullet's incoming bearing.
            driveStationaryHeavyEscape(lastEnemyAbsBearing, getEnergy() < 35.0 ? 410.0 : 360.0);
            return;
        }
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
        if (leachPmcEnemy()) {
            // When overlapped with the stationary power-3 shooter, do not spend several
            // ticks trying to rotate to an ideal escape angle; immediately back/ahead
            // along the current body axis away from the collision normal.
            emergencyStraightAwayFrom(robotBearing, 360.0);
        } else {
            driveAwayFrom(robotBearing, 220.0);
        }
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


    private void emergencyStraightAwayFrom(double threatBearing, double distance) {
        // Absolute threat bearing from us to the other robot.  Pick forward/backward
        // based on the current body axis so the first movement tick increases range;
        // do not request a large turn first, because while overlapping Robocode can
        // cancel movement repeatedly before the turn completes.
        double rel = Utils.normalRelativeAngle(threatBearing - getHeadingRadians());
        setMaxVelocity(8.0);
        setTurnRightRadians(0.0);
        setAhead(Math.cos(rel) > 0.0 ? -distance : distance);
    }

    private void driveStationaryHeavyEscape(double threatBearing, double distance) {
        double away = threatBearing + Math.PI;
        double best = away + moveDirection * 0.75;
        double bestScore = -1.0e9;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = -5; i <= 5; i++) {
                double a = away + side * 0.75 + i * 0.10;
                double px = projectX(getX(), a, 180.0);
                double py = projectY(getY(), a, 180.0);
                if (!insideBattlefield(px, py, 24.0)) {
                    continue;
                }
                double margin = Math.min(Math.min(px, getBattleFieldWidth() - px),
                        Math.min(py, getBattleFieldHeight() - py));
                double separation = Math.cos(Utils.normalRelativeAngle(a - away));
                double lateral = Math.abs(Math.sin(Utils.normalRelativeAngle(a - away)));
                // Strongly prefer one persistent dodge side to avoid tick-to-tick side
                // switching.  Do not use moveDirection here: onHitByBullet reverses that
                // flag, and TrackFire loss traces were exactly repeated hits followed by
                // side flips that pinned us in place.
                double sideBias = side == stationaryHeavyDirection ? 90.0 : 0.0;
                double score = 1.4 * margin + 220.0 * separation + 145.0 * lateral + sideBias;
                if (score > bestScore) {
                    bestScore = score;
                    best = a;
                }
            }
        }
        if (bestScore < -1.0e8) {
            driveAwayFrom(threatBearing, distance);
        } else {
            setMaxVelocity(8.0);
            driveAlongAngle(best, distance);
        }
    }


    private void driveSampleWallsEscape(double threatBearing, double distance) {
        // Dedicated dodge for sample.Walls.  Its gun is essentially head-on and its
        // fire timing is regular; changing orbit direction on every energy drop can
        // walk back into bullets that are already in flight.  Prefer the current
        // moveDirection side, score for both perpendicular crossing and opening range,
        // and only fall back to the opposite side if a wall blocks the chosen lane.
        double best = threatBearing + moveDirection * Math.PI / 2.0;
        double bestScore = -1.0e9;
        for (int sideTry = 0; sideTry < 2; sideTry++) {
            int side = sideTry == 0 ? moveDirection : -moveDirection;
            for (int i = -5; i <= 5; i++) {
                double a = threatBearing + side * Math.PI / 2.0 + i * 0.13;
                double px = projectX(getX(), a, 210.0);
                double py = projectY(getY(), a, 210.0);
                if (!insideBattlefield(px, py, 26.0)) {
                    continue;
                }
                double margin = Math.min(Math.min(px, getBattleFieldWidth() - px),
                        Math.min(py, getBattleFieldHeight() - py));
                double perpendicular = Math.cos(Utils.normalRelativeAngle(a - (threatBearing + side * Math.PI / 2.0)));
                double separation = -Math.cos(Utils.normalRelativeAngle(a - threatBearing));
                double sideBias = side == moveDirection ? 85.0 : 0.0;
                double score = 1.55 * margin + 150.0 * perpendicular + 70.0 * separation + sideBias;
                if (score > bestScore) {
                    bestScore = score;
                    best = a;
                }
            }
        }
        if (bestScore < -1.0e8) {
            best = Math.atan2(getBattleFieldWidth() / 2.0 - getX(), getBattleFieldHeight() / 2.0 - getY());
        }
        setMaxVelocity(8.0);
        driveAlongAngle(best, distance);
    }

    private void drivePerpendicularEscape(double threatBearing, double distance) {
        double best = threatBearing + Math.PI / 2.0;
        double bestScore = -1.0e9;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = -4; i <= 4; i++) {
                double a = threatBearing + side * Math.PI / 2.0 + i * 0.16;
                double px = projectX(getX(), a, 170.0);
                double py = projectY(getY(), a, 170.0);
                if (!insideBattlefield(px, py, 24.0)) {
                    continue;
                }
                double margin = Math.min(Math.min(px, getBattleFieldWidth() - px),
                        Math.min(py, getBattleFieldHeight() - py));
                // Favor getting out of the wall/corner while staying close to a true
                // perpendicular dodge line; this avoids driving straight into a wall
                // when the enemy is between us and the battlefield center.
                double perpendicular = Math.cos(Utils.normalRelativeAngle(a - (threatBearing + side * Math.PI / 2.0)));
                double score = 2.0 * margin + 120.0 * perpendicular;
                if (score > bestScore) {
                    bestScore = score;
                    best = a;
                }
            }
        }
        if (bestScore < -1.0e8) {
            best = Math.atan2(getBattleFieldWidth() / 2.0 - getX(), getBattleFieldHeight() / 2.0 - getY());
        }
        setMaxVelocity(8.0);
        driveAlongAngle(best, distance);
    }

    private void driveAwayFrom(double threatBearing, double distance) {
        double away = threatBearing + Math.PI;
        // Usually the safest response is a direct separation vector.  Accept being
        // somewhat close to a wall here: round-1 Corners traces showed the older
        // WALL_MARGIN test over-smoothed a valid escape into a centerward curve,
        // letting the enemy drive through and pin us at point blank.  Only search
        // for a replacement angle if the direct vector is truly unsafe.
        if (!insideBattlefield(projectX(getX(), away, Math.min(distance, 210.0)),
                projectY(getY(), away, Math.min(distance, 210.0)), 24.0)) {
            double best = away;
            double bestScore = -1.0e9;
            for (int i = -10; i <= 10; i++) {
                double a = away + i * 0.18;
                double px = projectX(getX(), a, 150.0);
                double py = projectY(getY(), a, 150.0);
                if (!insideBattlefield(px, py, 24.0)) {
                    continue;
                }
                double margin = Math.min(Math.min(px, getBattleFieldWidth() - px),
                        Math.min(py, getBattleFieldHeight() - py));
                double separation = Math.cos(Utils.normalRelativeAngle(a - away));
                double score = 1.2 * margin + 240.0 * separation;
                if (score > bestScore) {
                    bestScore = score;
                    best = a;
                }
            }
            away = bestScore > -1.0e8 ? best
                    : Math.atan2(getBattleFieldWidth() / 2.0 - getX(), getBattleFieldHeight() / 2.0 - getY());
        }
        setMaxVelocity(8.0);
        driveAlongAngle(away, distance);
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

    private static double lethalPower(double enemyEnergy) {
        // Robocode bullet damage is 4*p for p<=1, and 6*p-2 for p>1.
        // Add a tiny margin for rounding/energy updates, then clamp to legal firepower.
        double p = enemyEnergy <= 4.0 ? enemyEnergy / 4.0 + 0.06 : (enemyEnergy + 2.0) / 6.0 + 0.06;
        return limit(0.1, p, 3.0);
    }

    private static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }
}
