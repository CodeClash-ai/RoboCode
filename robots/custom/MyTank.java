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
    private static final double WALL_MARGIN = 42.0;
    private static final double PREFERRED_DISTANCE = 410.0;

    private int moveDirection = 1;
    private double lastEnemyEnergy = 100.0;
    private double lastEnemyHeading = 0.0;
    private boolean haveEnemyHeading = false;
    private long lastScanTime = -1000;
    private long lastDirectionChangeTime = -1000;
    private int stationaryScans = 0;
    private int enemyFireCount = 0;

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

        if (Math.abs(e.getVelocity()) < 0.05) {
            stationaryScans++;
        } else {
            stationaryScans = 0;
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
        double distanceOffset = limit(-0.62, (e.getDistance() - PREFERRED_DISTANCE) / 430.0, 0.55);
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
        }
        if (getEnergy() < 22 && distance > 260 && stationaryScans <= 5) {
            power = Math.min(power, 1.35);
        }
        if (getEnergy() < 9) {
            power = Math.min(power, 0.9);
        }
        power = Math.min(power, Math.max(0.1, getEnergy() - 0.15));

        double bulletSpeed = 20.0 - 3.0 * power;
        double predictedX = enemyX;
        double predictedY = enemyY;
        double predictedHeading = e.getHeadingRadians();
        double velocity = e.getVelocity();
        double turnRate = haveEnemyHeading ? Utils.normalRelativeAngle(e.getHeadingRadians() - lastEnemyHeading) : 0.0;

        // Circular prediction when the enemy is consistently turning, linear
        // prediction otherwise.  Clamp at the wall, because many bots turn or
        // stop there and unclamped prediction tends to shoot outside the field.
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

        double aim = Math.atan2(predictedX - getX(), predictedY - getY());
        setTurnGunRightRadians(Utils.normalRelativeAngle(aim - getGunHeadingRadians()));

        // Fire when the gun is essentially on target.  The tolerance scales with
        // target width, so we still shoot promptly at close range.
        double tolerance = Math.atan2(28.0, distance);
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemainingRadians()) < tolerance && getEnergy() > 0.25) {
            setFire(power);
        }
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
        while (!insideBattlefield(projectX(getX(), smoothed, 155.0), projectY(getY(), smoothed, 155.0), WALL_MARGIN)
                && tries++ < 28) {
            smoothed += orientation * 0.075;
        }
        if (insideBattlefield(projectX(getX(), smoothed, 155.0), projectY(getY(), smoothed, 155.0), WALL_MARGIN)) {
            return smoothed;
        }

        // If the preferred smoothing direction fails (common when spawned in a
        // corner), try the other way before falling back to the center escape.
        smoothed = angle;
        tries = 0;
        while (!insideBattlefield(projectX(getX(), smoothed, 155.0), projectY(getY(), smoothed, 155.0), WALL_MARGIN)
                && tries++ < 28) {
            smoothed -= orientation * 0.075;
        }
        if (insideBattlefield(projectX(getX(), smoothed, 155.0), projectY(getY(), smoothed, 155.0), WALL_MARGIN)) {
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
