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
    private int wallEnemyScans = 0;
    private double enemyVelocityAvg = 0.0;
    private double enemyTurnRateAvg = 0.0;

    // Lightweight virtual guns.  The latest opponent dodges enough that pure
    // circular prediction over-leads badly; keep rolling errors for several
    // aim styles and let the bot pick the one matching the current enemy.
    private static final int GUN_HEAD_ON = 0;
    private static final int GUN_LINEAR = 1;
    private static final int GUN_CIRCULAR = 2;
    private static final int GUN_AVERAGED = 3;
    private static final int GUN_GUESS_FACTOR = 4;
    private static final int GUN_COUNT = 5;
    private static final int VIRTUAL_WAVES = 96;
    private final double[] virtualGunError = {55.0, 60.0, 60.0, 58.0, 70.0};
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
        if (enemyDrop > 0.09 && enemyDrop <= 3.01) {      // likely enemy bullet
            enemyFireCount++;
            reverseDirection();
        }

        // SittingDuck-style opponents in the current logs never fire.  Once we
        // are confident a stationary target is harmless, cancel movement so we
        // do not donate wall/collision damage while the gun farms max-power
        // hits.  If a stationary locker does fire, enemyFireCount disables this
        // branch and we keep orbiting/dodging as normal.
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
        double preferredDistance = wallEnemyScans > 4 ? 315.0 : (headOnGunIsBest() ? 330.0 : (slowEnemyScans > 12 ? 285.0 : PREFERRED_DISTANCE));
        // Against the current GF-style opponent our gun struggles mostly due
        // to long bullet flight, while its own gun almost never connects.  Once
        // virtual guns report a hard-to-hit mover, tighten the orbit a bit to
        // shorten flight time and improve hit/kill speed without going to ram range.
        if (virtualSamples > 28 && bestGunError() > 72.0 && stationaryScans <= 5 && slowEnemyScans <= 12) {
            preferredDistance = 355.0;
        }
        double distanceOffset = limit(-0.62, (e.getDistance() - preferredDistance) / 430.0, 0.55);
        double desired = absBearing + moveDirection * (Math.PI / 2.0 - distanceOffset);
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
        } else if (wallEnemyScans > 4 && getEnergy() > 14 && distance < 820) {
            // Wall-huggers have very limited escape room; use max-power
            // head-on/near-head-on shots to finish them before they can spend
            // energy on stray bullets (which lowers our available bullet score).
            power = 3.0;
        } else if (headOnGunIsBest() && getEnergy() > 18 && distance < 720) {
            // The current DeepThought opponent dodges/reverses enough that a
            // head-on gun wins the virtual-gun race.  Once detected, spend more
            // energy on heavier bullets: its own hit rate is tiny, and the
            // shorter rounds are worth the slightly slower bullet speed.
            power = Math.max(power, distance < 360 ? 3.0 : (distance < 520 ? 2.8 : 2.35));
        } else if (slowEnemyScans > 8 && getEnergy() > 12 && distance < 720) {
            // The current recorded opponent is a very slow stop-and-go shooter.
            // Once a target has proven it cannot exceed about speed 3, heavier
            // bullets trade a little travel time for much faster damage and a
            // larger bullet bonus.  Fast/unknown movers keep the safer ladder.
            power = 3.0;
        }
        boolean hardToHitMover = virtualSamples > 28 && bestGunError() > 72.0 && stationaryScans <= 5 && slowEnemyScans <= 12;
        // If all virtual guns are missing badly (as with wave-surfing GF-style
        // enemies), do not gamble the whole energy stack on repeated heavy
        // bullets.  Use tiny bullets at low energy: a hit gives more energy back
        // than it costs, while misses cannot self-kill us quickly.
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
        double turnRate = haveEnemyHeading ? Utils.normalRelativeAngle(e.getHeadingRadians() - lastEnemyHeading) : 0.0;

        double[][] candidates = new double[GUN_COUNT][2];
        candidates[GUN_HEAD_ON] = predictEnemy(enemyX, enemyY, e.getHeadingRadians(), e.getVelocity(), 0.0, bulletSpeed, GUN_HEAD_ON);
        candidates[GUN_LINEAR] = predictEnemy(enemyX, enemyY, e.getHeadingRadians(), e.getVelocity(), 0.0, bulletSpeed, GUN_LINEAR);
        candidates[GUN_CIRCULAR] = predictEnemy(enemyX, enemyY, e.getHeadingRadians(), e.getVelocity(), turnRate, bulletSpeed, GUN_CIRCULAR);
        candidates[GUN_AVERAGED] = predictEnemy(enemyX, enemyY, e.getHeadingRadians(), e.getVelocity(), turnRate, bulletSpeed, GUN_AVERAGED);
        candidates[GUN_GUESS_FACTOR] = predictGuessFactor(absBearing, distance, bulletSpeed);
        addVirtualWave(candidates, bulletSpeed, absBearing);

        int gun = chooseGun();
        if (stationaryScans > 5) {
            gun = GUN_HEAD_ON;
        } else if (wallEnemyScans > 4 && Math.abs(e.getVelocity()) > 3.0 && Math.abs(turnRate) < 0.025) {
            // Antiwalls-style bots often sit still, then run in a straight line
            // along an edge.  During those fast/straight wall bursts, full
            // linear prediction is much better than the damped wall-stop gun.
            gun = GUN_LINEAR;
        } else if (wallEnemyScans > 4 && virtualSamples < 18) {
            // Cold-start wall-bound targets with the damped wall predictor, but
            // do not force it forever.  The current antiwalls opponent slides
            // in long straight bursts along an edge, where the virtual guns
            // quickly learn that full linear/circular prediction is better
            // than the damped wall shot used for prior stop/reverse wall bots.
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
        double bestOther = Math.min(Math.min(Math.min(virtualGunError[GUN_LINEAR], virtualGunError[GUN_CIRCULAR]), virtualGunError[GUN_AVERAGED]), virtualGunError[GUN_GUESS_FACTOR]);
        return virtualGunError[GUN_HEAD_ON] <= bestOther + 3.0;
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
        if (wallEnemyScans > 4 && gunType == GUN_AVERAGED) {
            // A wall-bound bot often alternates between max-speed bursts and
            // hard stops/reverses.  A damped linear projection was slightly
            // better than pure head-on in trace replay, while still avoiding
            // the heavy over-lead of full circular/linear prediction.
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
        double gunTurn = Utils.normalRelativeAngle(getHeadingRadians() + e.getBearingRadians() - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);
        if (e.isMyFault()) {
            setBack(90);
        } else {
            setAhead(90 * moveDirection);
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
