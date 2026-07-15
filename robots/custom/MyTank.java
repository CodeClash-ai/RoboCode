package custom;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.*;

public class MyTank extends AdvancedRobot {

    private double lastEnemyHeading = 0;
    private double lastEnemyEnergy = 100.0;
    private int moveDirection = 1;

    public void run() {
        setBodyColor(Color.black);
        setGunColor(Color.darkGray);
        setRadarColor(Color.red);
        setBulletColor(Color.orange);
        setScanColor(Color.red);

        setAdjustRadarForRobotTurn(true);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        turnRadarRightRadians(Double.POSITIVE_INFINITY);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // Precise radar lock
        double absoluteBearing = getHeadingRadians() + e.getBearingRadians();
        double radarTurn = absoluteBearing - getRadarHeadingRadians();
        setTurnRadarRightRadians(Utils.normalRelativeAngle(radarTurn) * 1.5);

        // Enemy bullet firing detection
        double energyDrop = lastEnemyEnergy - e.getEnergy();
        if (energyDrop >= 0.1 && energyDrop <= 3.0) {
            // Dodge immediately on enemy firing
            if (Math.random() < 0.5) {
                moveDirection = -moveDirection;
            }
        }
        lastEnemyEnergy = e.getEnergy();

        double bulletPower = 3.0;
        if (e.getDistance() > 400) {
            bulletPower = 1.5;
        } else if (e.getDistance() > 200) {
            bulletPower = 2.0;
        }
        bulletPower = Math.min(bulletPower, getEnergy() / 6.0);
        if (bulletPower < 0.1) bulletPower = 0.1;
        
        double bulletSpeed = 20 - 3 * bulletPower;

        double enemyX = getX() + e.getDistance() * Math.sin(absoluteBearing);
        double enemyY = getY() + e.getDistance() * Math.cos(absoluteBearing);
        
        double enemyHeading = e.getHeadingRadians();
        double enemyVelocity = e.getVelocity();
        double headingChange = enemyHeading - lastEnemyHeading;
        lastEnemyHeading = enemyHeading;
        
        double predictedX = enemyX;
        double predictedY = enemyY;
        
        for (int i = 0; i < 20; i++) {
            double distance = Point2D.distance(getX(), getY(), predictedX, predictedY);
            double t = distance / bulletSpeed;
            
            if (Math.abs(headingChange) > 0.00001) {
                predictedX = enemyX + (enemyVelocity / headingChange) * (Math.cos(enemyHeading) - Math.cos(enemyHeading + headingChange * t));
                predictedY = enemyY + (enemyVelocity / headingChange) * (Math.sin(enemyHeading + headingChange * t) - Math.sin(enemyHeading));
            } else {
                predictedX = enemyX + enemyVelocity * Math.sin(enemyHeading) * t;
                predictedY = enemyY + enemyVelocity * Math.cos(enemyHeading) * t;
            }
            
            double margin = 18.0;
            predictedX = Math.max(margin, Math.min(getBattleFieldWidth() - margin, predictedX));
            predictedY = Math.max(margin, Math.min(getBattleFieldHeight() - margin, predictedY));
        }
        
        double gunTurn = Utils.normalRelativeAngle(Math.atan2(predictedX - getX(), predictedY - getY()) - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(bulletPower);
        }

        // Improved movement and wall smoothing
        double preferredDistance = 350.0;
        double approachAngle = 0.0;
        if (e.getDistance() > preferredDistance + 50) {
            approachAngle = 0.3 * moveDirection; 
        } else if (e.getDistance() < preferredDistance - 50) {
            approachAngle = -0.3 * moveDirection;
        }
        double targetAngle = absoluteBearing + Math.PI / 2 + approachAngle;
        
        double nextX = getX() + 100 * Math.sin(targetAngle);
        double nextY = getY() + 100 * Math.cos(targetAngle);
        
        double wallMargin = 45.0;
        if (nextX < wallMargin || nextX > getBattleFieldWidth() - wallMargin ||
            nextY < wallMargin || nextY > getBattleFieldHeight() - wallMargin) {
            moveDirection = -moveDirection;
            targetAngle = absoluteBearing + Math.PI / 2 + (-approachAngle);
        }
        
        setTurnRightRadians(Utils.normalRelativeAngle(targetAngle - getHeadingRadians()));
        setAhead(100 * moveDirection);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        moveDirection = -moveDirection;
    }

    public void onHitWall(HitWallEvent e) {
        moveDirection = -moveDirection;
        setAhead(100 * moveDirection);
    }
    
    public void onHitRobot(HitRobotEvent e) {
        if (e.getEnergy() < getEnergy()) {
            setAhead(100);
        } else {
            moveDirection = -moveDirection;
            setAhead(100 * moveDirection);
        }
    }
}
