package custom;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.*;

public class MyTank extends AdvancedRobot {

    private double lastEnemyHeading = 0;
    private double lastEnemyEnergy = 100.0;
    private int moveDirection = 1;
    private int scanDirection = 1;

    public void run() {
        setBodyColor(Color.black);
        setGunColor(Color.darkGray);
        setRadarColor(Color.red);
        setBulletColor(Color.orange);
        setScanColor(Color.red);

        setAdjustRadarForRobotTurn(true);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // Continuous radar sweep
        turnRadarRightRadians(Double.POSITIVE_INFINITY);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // Radar Lock (optimized for speed and maintaining lock)
        double absoluteBearing = getHeadingRadians() + e.getBearingRadians();
        double radarTurn = absoluteBearing - getRadarHeadingRadians();
        setTurnRadarRightRadians(Utils.normalRelativeAngle(radarTurn) * 1.5);

        // Detect if enemy fired (energy drop between 0.1 and 3.0)
        double energyDrop = lastEnemyEnergy - e.getEnergy();
        if (energyDrop >= 0.1 && energyDrop <= 3.0) {
            // High probability of enemy bullet fired; evade by changing direction and distance
            if (Math.random() < 0.6) {
                moveDirection = -moveDirection;
            }
        }
        lastEnemyEnergy = e.getEnergy();

        // Dynamic bullet power: higher power when closer or we have a significant energy lead
        double distance = e.getDistance();
        double bulletPower = 2.0;
        if (distance < 150) {
            bulletPower = 3.0;
        } else if (distance > 450) {
            bulletPower = 1.0;
        }
        // Never fire bullet that would leave us vulnerable
        bulletPower = Math.min(bulletPower, getEnergy() / 5.0);
        if (bulletPower < 0.1) bulletPower = 0.1;

        double bulletSpeed = 20.0 - 3.0 * bulletPower;

        // Enemy state estimation
        double enemyX = getX() + distance * Math.sin(absoluteBearing);
        double enemyY = getY() + distance * Math.cos(absoluteBearing);
        double enemyHeading = e.getHeadingRadians();
        double enemyVelocity = e.getVelocity();
        double headingChange = Utils.normalRelativeAngle(enemyHeading - lastEnemyHeading);
        lastEnemyHeading = enemyHeading;

        // Circular/Linear predictive targeting
        double predictedX = enemyX;
        double predictedY = enemyY;
        for (int i = 0; i < 20; i++) {
            double d = Point2D.distance(getX(), getY(), predictedX, predictedY);
            double t = d / bulletSpeed;

            if (Math.abs(headingChange) > 0.0001) {
                predictedX = enemyX + (enemyVelocity / headingChange) * (Math.cos(enemyHeading) - Math.cos(enemyHeading + headingChange * t));
                predictedY = enemyY + (enemyVelocity / headingChange) * (Math.sin(enemyHeading + headingChange * t) - Math.sin(enemyHeading));
            } else {
                predictedX = enemyX + enemyVelocity * Math.sin(enemyHeading) * t;
                predictedY = enemyY + enemyVelocity * Math.cos(enemyHeading) * t;
            }

            // Clip prediction within the arena boundaries with safety margins
            double margin = 20.0;
            predictedX = Math.max(margin, Math.min(getBattleFieldWidth() - margin, predictedX));
            predictedY = Math.max(margin, Math.min(getBattleFieldHeight() - margin, predictedY));
        }

        // Align and fire gun
        double gunTurn = Utils.normalRelativeAngle(Math.atan2(predictedX - getX(), predictedY - getY()) - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 0.1) {
            setFire(bulletPower);
        }

        // Perpendicular movement with wall-smoothing and distance control
        // Try to stay around 300-400px from the enemy
        double preferredDistance = 350.0;
        double approachAngle = 0.0;
        if (distance > preferredDistance + 50) {
            approachAngle = 0.35 * moveDirection; // Approach
        } else if (distance < preferredDistance - 50) {
            approachAngle = -0.35 * moveDirection; // Retreat
        }
        double targetAngle = absoluteBearing + Math.PI / 2 + approachAngle;

        // Wall smoothing / avoidance
        double wallMargin = 50.0;
        double nextX = getX() + 100 * Math.sin(targetAngle);
        double nextY = getY() + 100 * Math.cos(targetAngle);

        if (nextX < wallMargin || nextX > getBattleFieldWidth() - wallMargin ||
            nextY < wallMargin || nextY > getBattleFieldHeight() - wallMargin) {
            // Change movement direction on collision path
            moveDirection = -moveDirection;
            targetAngle = absoluteBearing + Math.PI / 2 + (-approachAngle);
        }

        setTurnRightRadians(Utils.normalRelativeAngle(targetAngle - getHeadingRadians()));
        setAhead(100 * moveDirection);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Change direction immediately upon hit to throw off targeting
        moveDirection = -moveDirection;
        setAhead(150 * moveDirection);
    }

    public void onHitWall(HitWallEvent e) {
        moveDirection = -moveDirection;
        setAhead(120 * moveDirection);
    }

    public void onHitRobot(HitRobotEvent e) {
        moveDirection = -moveDirection;
        setAhead(120 * moveDirection);
    }
}
