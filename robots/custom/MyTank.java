package custom;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.HitByBulletEvent;
import robocode.HitWallEvent;
import robocode.HitRobotEvent;
import robocode.DeathEvent;
import robocode.util.Utils;
import java.awt.geom.Point2D;
import java.awt.Color;

/**
 * MyTank - a competitive 1v1 robot.
 *
 * Strategy:
 *  - Radar: narrow "lock" sweep on the single enemy (infinite lock for 1v1).
 *  - Gun: predictive targeting. Uses a simple statistical/circular-linear
 *    predictor plus head-on fallback, picks bullet power based on distance & energy.
 *  - Movement: orbital (perpendicular) movement around the enemy with
 *    wall smoothing, random reversals, and evasive reaction to being hit.
 */
public class MyTank extends AdvancedRobot {

    // Enemy tracking
    private double enemyX, enemyY;
    private double enemyEnergy = 100;
    private double enemyHeading = 0;
    private double enemyVelocity = 0;
    private double enemyDistance = 1000;
    private double enemyBearing = 0;
    private boolean enemyScanned = false;
    private long lastScanTime = 0;

    // Movement
    private int moveDirection = 1;
    private double lastEnemyEnergy = 100;

    // Circular targeting: track enemy turn rate
    private double lastEnemyHeading = 0;
    private double enemyTurnRate = 0;
    private boolean haveLastHeading = false;

    public void run() {
        setColors(Color.BLUE, Color.CYAN, Color.WHITE);

        // Let radar and gun turn independently of body
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        // Initial radar sweep to find enemy
        turnRadarRight(Double.POSITIVE_INFINITY);

        while (true) {
            // If we haven't scanned recently, keep the radar sweeping.
            if (getTime() - lastScanTime > 4) {
                setTurnRadarRight(Double.POSITIVE_INFINITY);
            }
            doMovement();
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        lastScanTime = getTime();
        enemyScanned = true;

        double absBearing = getHeadingRadians() + e.getBearingRadians();
        enemyBearing = absBearing;
        enemyDistance = e.getDistance();
        enemyHeading = e.getHeadingRadians();
        enemyVelocity = e.getVelocity();

        // Track enemy turn rate for circular prediction
        if (haveLastHeading) {
            double dh = Utils.normalRelativeAngle(enemyHeading - lastEnemyHeading);
            enemyTurnRate = Math.max(-0.15, Math.min(0.15, dh));
        }
        lastEnemyHeading = enemyHeading;
        haveLastHeading = true;

        // Enemy absolute position
        enemyX = getX() + Math.sin(absBearing) * enemyDistance;
        enemyY = getY() + Math.cos(absBearing) * enemyDistance;

        lastEnemyEnergy = enemyEnergy;
        enemyEnergy = e.getEnergy();

        // ---- Radar lock ----
        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        // Widen slightly for a robust lock
        setTurnRadarRightRadians(radarTurn * 2.0);

        // ---- Gun: predictive aim ----
        aimAndFire(absBearing);

        // ---- Movement ----
        doMovement();
    }

    private void aimAndFire(double absBearing) {
        // Choose bullet power by distance & our energy
        double power;
        if (enemyDistance < 150) {
            power = 3.0;
        } else if (enemyDistance < 400) {
            power = 2.5;
        } else {
            power = 1.9;
        }
        // Against a (near-)stationary target we always hit, so fire max power to
        // kill faster and maximize damage margin (safe: no aiming error).
        if (Math.abs(enemyVelocity) < 3.5) {
            power = 3.0;
        }
        if (getEnergy() < 20) {
            power = Math.min(power, 1.0);
        }
        if (getEnergy() < 8) {
            power = Math.min(power, 0.5);
        }
        power = Math.max(0.1, Math.min(power, 3.0));

        double bulletSpeed = 20 - 3 * power;

        // Predict enemy future position (iterative circular prediction)
        double predX = enemyX;
        double predY = enemyY;
        double eHeading = enemyHeading;
        double eVel = enemyVelocity;

        // Estimate turn rate from small movement changes is unreliable;
        // use straight-line + heading prediction over bullet flight time.
        double deltaTime = 0;
        double battleW = getBattleFieldWidth();
        double battleH = getBattleFieldHeight();
        double predDist;
        do {
            deltaTime++;
            eHeading += enemyTurnRate;
            predX += Math.sin(eHeading) * eVel;
            predY += Math.cos(eHeading) * eVel;
            // Keep prediction inside the field
            if (predX < 18) predX = 18;
            if (predX > battleW - 18) predX = battleW - 18;
            if (predY < 18) predY = 18;
            if (predY > battleH - 18) predY = battleH - 18;
            predDist = Point2D.distance(getX(), getY(), predX, predY);
        } while ((deltaTime) * bulletSpeed < predDist && deltaTime < 120);

        // Stop-and-go / slow target: the enemy stops for ~half its ticks, so a
        // constant-velocity predictor over-shoots. Blend current position (heavy)
        // with the linear prediction to compensate.
        if (Math.abs(enemyVelocity) < 3.5) {
            predX = 0.65 * enemyX + 0.35 * predX;
            predY = 0.65 * enemyY + 0.35 * predY;
        }
        double aimAngle = Math.atan2(predX - getX(), predY - getY());
        double gunTurn = Utils.normalRelativeAngle(aimAngle - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        // Only fire if gun is roughly aligned and cool
        if (getGunHeat() == 0 && Math.abs(gunTurn) < 0.15 && getEnergy() > power + 0.2) {
            setFire(power);
        }
    }

    private void doMovement() {
        if (!enemyScanned) {
            return;
        }

        // Detect enemy firing: energy drop between 0.1 and 3.0
        double energyDrop = lastEnemyEnergy - enemyEnergy;
        boolean enemyFired = energyDrop >= 0.09 && energyDrop <= 3.0;

        // Orbit: move perpendicular to enemy
        double absBearing = enemyBearing;
        // Desired heading perpendicular to the enemy, offset a bit to close/open range
        double desiredDir = absBearing + Math.PI / 2 * moveDirection;

        // Wall smoothing: steer away from walls
        desiredDir = wallSmoothing(getX(), getY(), desiredDir, moveDirection);

        double turn = Utils.normalRelativeAngle(desiredDir - getHeadingRadians());

        // If turn is > 90deg, drive backwards instead (smoother)
        double moveAmount = 100;
        if (Math.abs(turn) > Math.PI / 2) {
            turn = Utils.normalRelativeAngle(turn + Math.PI);
            moveAmount = -moveAmount;
        }

        setTurnRightRadians(turn);
        setAhead(moveAmount);

        // Reverse direction periodically or when enemy fires (dodge)
        if (enemyFired) {
            if (Math.random() < 0.6) {
                moveDirection = -moveDirection;
            }
        } else if (Math.random() < 0.03) {
            moveDirection = -moveDirection;
        }
    }

    /**
     * Wall smoothing: adjust desired absolute heading so we don't crash into walls.
     */
    private double wallSmoothing(double x, double y, double desiredDir, int dir) {
        double stick = 140;
        double w = getBattleFieldWidth();
        double h = getBattleFieldHeight();
        double angle = desiredDir;
        int attempts = 0;
        while (attempts < 36) {
            double testX = x + Math.sin(angle) * stick;
            double testY = y + Math.cos(angle) * stick;
            if (testX > 30 && testX < w - 30 && testY > 30 && testY < h - 30) {
                break;
            }
            // rotate away
            angle += dir * 0.15;
            attempts++;
        }
        return angle;
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Change direction when hit to be less predictable
        if (Math.random() < 0.5) {
            moveDirection = -moveDirection;
        }
    }

    public void onHitWall(HitWallEvent e) {
        moveDirection = -moveDirection;
    }

    public void onHitRobot(HitRobotEvent e) {
        // Ram some damage but mostly back off
        moveDirection = -moveDirection;
        if (e.isMyFault()) {
            setBack(50);
        }
    }

    public void onDeath(DeathEvent e) {
        // nothing
    }
}
