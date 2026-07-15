package custom;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.*;

public class MyTank extends AdvancedRobot {

    private double lastEnemyHeading = 0;
    private int moveDirection = 1;

    public void run() {
        // Set colors
        setBodyColor(Color.black);
        setGunColor(Color.darkGray);
        setRadarColor(Color.red);
        setBulletColor(Color.orange);
        setScanColor(Color.red);

        // Keep radar and gun independent from body movement
        setAdjustRadarForRobotTurn(true);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // Continuous radar sweep
        turnRadarRightRadians(Double.POSITIVE_INFINITY);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // Lock radar on target (radar slip prevention / lock)
        double radarTurn = getHeadingRadians() + e.getBearingRadians() - getRadarHeadingRadians();
        setTurnRadarRightRadians(Utils.normalRelativeAngle(radarTurn) * 1.5);

        // Calculate bullet power based on distance and our energy
        double bulletPower = 3.0;
        if (e.getDistance() > 400) {
            bulletPower = 1.5;
        } else if (e.getDistance() > 200) {
            bulletPower = 2.0;
        }
        bulletPower = Math.min(bulletPower, getEnergy() / 6.0);
        if (bulletPower < 0.1) bulletPower = 0.1;
        
        double bulletSpeed = 20 - 3 * bulletPower;

        double absoluteBearing = getHeadingRadians() + e.getBearingRadians();
        double enemyX = getX() + e.getDistance() * Math.sin(absoluteBearing);
        double enemyY = getY() + e.getDistance() * Math.cos(absoluteBearing);
        
        double enemyHeading = e.getHeadingRadians();
        double enemyVelocity = e.getVelocity();
        double headingChange = enemyHeading - lastEnemyHeading;
        lastEnemyHeading = enemyHeading;
        
        // Iterative predictive aim (handling both linear and circular patterns)
        double predictedX = enemyX;
        double predictedY = enemyY;
        
        for (int i = 0; i < 20; i++) {
            double distance = Point2D.distance(getX(), getY(), predictedX, predictedY);
            double t = distance / bulletSpeed;
            
            if (Math.abs(headingChange) > 0.00001) {
                // Circular motion prediction
                predictedX = enemyX + (enemyVelocity / headingChange) * (Math.cos(enemyHeading) - Math.cos(enemyHeading + headingChange * t));
                predictedY = enemyY + (enemyVelocity / headingChange) * (Math.sin(enemyHeading + headingChange * t) - Math.sin(enemyHeading));
            } else {
                // Linear prediction
                predictedX = enemyX + enemyVelocity * Math.sin(enemyHeading) * t;
                predictedY = enemyY + enemyVelocity * Math.cos(enemyHeading) * t;
            }
            
            // Clip predicted position to battlefield boundaries
            double margin = 18.0;
            predictedX = Math.max(margin, Math.min(getBattleFieldWidth() - margin, predictedX));
            predictedY = Math.max(margin, Math.min(getBattleFieldHeight() - margin, predictedY));
        }
        
        double gunTurn = Utils.normalRelativeAngle(Math.atan2(predictedX - getX(), predictedY - getY()) - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        // Fire if gun is reasonably aligned and cool
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(bulletPower);
        }

        // Perpendicular movement with Wall Smoothing/Avoidance
        double targetAngle = absoluteBearing + Math.PI / 2 + (moveDirection * 0.4);
        
        // Predict where we would end up in 80 pixels
        double nextX = getX() + 80 * Math.sin(targetAngle);
        double nextY = getY() + 80 * Math.cos(targetAngle);
        
        double wallMargin = 45.0;
        if (nextX < wallMargin || nextX > getBattleFieldWidth() - wallMargin ||
            nextY < wallMargin || nextY > getBattleFieldHeight() - wallMargin) {
            // Reverse direction to slide/avoid wall
            moveDirection = -moveDirection;
            targetAngle = absoluteBearing + Math.PI / 2 + (moveDirection * 0.4);
        }
        
        setTurnRightRadians(Utils.normalRelativeAngle(targetAngle - getHeadingRadians()));
        setAhead(100 * moveDirection);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Change direction to throw off targeting
        moveDirection = -moveDirection;
    }

    public void onHitWall(HitWallEvent e) {
        // Backup and turn
        moveDirection = -moveDirection;
        setAhead(100 * moveDirection);
    }
    
    public void onHitRobot(HitRobotEvent e) {
        // Back off or ram depending on relative energy
        if (e.getEnergy() < getEnergy()) {
            setAhead(100);
        } else {
            moveDirection = -moveDirection;
            setAhead(100 * moveDirection);
        }
    }
}
