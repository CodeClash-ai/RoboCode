package custom;

import java.awt.Color;
import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

/**
 * MyTank - compact 1v1 AdvancedRobot.
 *
 * Strategy:
 *  - independent radar with a tight lock once the opponent is seen
 *  - linear predictive targeting (excellent against corner/wall/sample bots)
 *  - perpendicular orbiting with direction changes on enemy fire / wall / impacts
 *  - distance management and wall-safe turns to avoid becoming a stationary target
 */
public class MyTank extends AdvancedRobot {
    private static final double WALL_MARGIN = 72;

    private int moveDirection = 1;
    private double lastEnemyEnergy = 100.0;
    private long lastScanTime = 0;

    public void run() {
        setBodyColor(new Color(20, 20, 30));
        setGunColor(new Color(230, 190, 40));
        setRadarColor(new Color(90, 210, 255));
        setBulletColor(Color.WHITE);
        setScanColor(Color.CYAN);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // Keep moving even before first scan; the radar spin will find the enemy.
        setAhead(120);
        setTurnRadarRight(Double.POSITIVE_INFINITY);
        while (true) {
            if (getTime() - lastScanTime > 18) {
                setTurnRadarRight(Double.POSITIVE_INFINITY);
            }
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        lastScanTime = getTime();

        double absBearing = getHeadingRadians() + e.getBearingRadians();
        double enemyX = getX() + Math.sin(absBearing) * e.getDistance();
        double enemyY = getY() + Math.cos(absBearing) * e.getDistance();

        // Tight radar lock: overshoot a little so we do not lose fast movers.
        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 2.0);

        doMovement(e, absBearing, enemyX, enemyY);
        doGun(e, absBearing, enemyX, enemyY);

        lastEnemyEnergy = e.getEnergy();
    }

    private void doMovement(ScannedRobotEvent e, double absBearing, double enemyX, double enemyY) {
        double enemyDrop = lastEnemyEnergy - e.getEnergy();
        if (enemyDrop > 0.09 && enemyDrop <= 3.01) { // enemy probably fired
            moveDirection = -moveDirection;
        }
        if (getTime() % 47 == 0 || e.getDistance() < 140 || e.getDistance() > 560) {
            moveDirection = -moveDirection;
        }

        // Orbit mostly perpendicular.  Add distance correction: close in when far,
        // widen when too close.  This also makes our path less linear.
        double turn = e.getBearing() + 90.0;
        if (e.getDistance() > 430) {
            turn -= 25.0 * moveDirection;
        } else if (e.getDistance() < 230) {
            turn += 35.0 * moveDirection;
        }

        // Wall smoothing: if the projected point is unsafe, flip and turn away.
        double projectedHeading = getHeadingRadians() + Math.toRadians(turn);
        double px = getX() + Math.sin(projectedHeading) * 120.0 * moveDirection;
        double py = getY() + Math.cos(projectedHeading) * 120.0 * moveDirection;
        if (!insideBattlefield(px, py)) {
            moveDirection = -moveDirection;
            turn += 75.0;
        }

        setTurnRight(Utils.normalRelativeAngleDegrees(turn));
        setAhead(150.0 * moveDirection);
        setMaxVelocity(Math.abs(getTurnRemaining()) > 45 ? 6.0 : 8.0);
    }

    private void doGun(ScannedRobotEvent e, double absBearing, double enemyX, double enemyY) {
        double distance = e.getDistance();
        double power;
        if (distance < 170) {
            power = 3.0;
        } else if (distance < 360) {
            power = 2.4;
        } else if (distance < 560) {
            power = 1.8;
        } else {
            power = 1.2;
        }
        power = Math.min(power, Math.max(0.1, getEnergy() - 0.2));
        if (getEnergy() < 18 && distance > 260) {
            power = Math.min(power, 1.4);
        }

        // Iterative linear prediction.  Clamp predictions to the field, which is
        // especially useful against corner/wall movers.
        double bulletSpeed = 20.0 - 3.0 * power;
        double predictedX = enemyX;
        double predictedY = enemyY;
        double enemyHeading = e.getHeadingRadians();
        double enemyVelocity = e.getVelocity();
        double deltaTime = 0.0;
        while (++deltaTime * bulletSpeed < distance(getX(), getY(), predictedX, predictedY)
                && deltaTime < 80) {
            predictedX += Math.sin(enemyHeading) * enemyVelocity;
            predictedY += Math.cos(enemyHeading) * enemyVelocity;
            predictedX = limit(18.0, predictedX, getBattleFieldWidth() - 18.0);
            predictedY = limit(18.0, predictedY, getBattleFieldHeight() - 18.0);
        }

        double aim = Math.atan2(predictedX - getX(), predictedY - getY());
        setTurnGunRightRadians(Utils.normalRelativeAngle(aim - getGunHeadingRadians()));

        double gunError = Math.abs(getGunTurnRemainingRadians());
        if (getGunHeat() == 0 && gunError < Math.atan2(36.0, distance) && getEnergy() > 0.3) {
            setFire(power);
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        moveDirection = -moveDirection;
        setTurnRight(Utils.normalRelativeAngleDegrees(90.0 - e.getBearing()));
        setAhead(170.0 * moveDirection);
    }

    public void onHitWall(HitWallEvent e) {
        moveDirection = -moveDirection;
        setBack(120);
        setTurnRight(60);
    }

    public void onHitRobot(HitRobotEvent e) {
        moveDirection = -moveDirection;
        setTurnGunRight(Utils.normalRelativeAngleDegrees(getHeading() + e.getBearing() - getGunHeading()));
        if (getGunHeat() == 0 && getEnergy() > 3) {
            setFire(3.0);
        }
        setBack(80);
    }

    private boolean insideBattlefield(double x, double y) {
        return x > WALL_MARGIN && x < getBattleFieldWidth() - WALL_MARGIN
                && y > WALL_MARGIN && y < getBattleFieldHeight() - WALL_MARGIN;
    }

    private static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }
}
