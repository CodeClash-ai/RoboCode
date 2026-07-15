package custom;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.HitByBulletEvent;
import robocode.HitWallEvent;
import robocode.BulletHitEvent;
import robocode.util.Utils;
import java.awt.geom.Point2D;

public class MyTank extends AdvancedRobot {
    private double moveDirection = 1;

    public void run() {
        setAdjustRadarForGunTurn(true);
        setAdjustGunForRobotTurn(true);

        while (true) {
            // Sweep radar to find opponent
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double absoluteBearing = getHeadingRadians() + e.getBearingRadians();
        
        // Radar Lock: keep the radar locked on the target
        double radarTurn = Utils.normalRelativeAngle(absoluteBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 1.9);

        // Circular Targeting / Predictive Aiming
        double bulletPower = Math.min(3.0, getEnergy() / 10);
        if (e.getDistance() > 150) {
            bulletPower = 2.0;
        }
        if (e.getDistance() > 300) {
            bulletPower = 1.0;
        }
        double bulletSpeed = 20 - 3 * bulletPower;
        
        double enemyHeading = e.getHeadingRadians();
        double enemyVelocity = e.getVelocity();
        
        double predictedX = getX() + e.getDistance() * Math.sin(absoluteBearing);
        double predictedY = getY() + e.getDistance() * Math.cos(absoluteBearing);
        
        // Simple linear/circular estimation
        double deltaTime = 0;
        double battleFieldWidth = getBattleFieldWidth();
        double battleFieldHeight = getBattleFieldHeight();
        
        while ((++deltaTime) * bulletSpeed < Point2D.distance(getX(), getY(), predictedX, predictedY)) {
            predictedX += enemyVelocity * Math.sin(enemyHeading);
            predictedY += enemyVelocity * Math.cos(enemyHeading);
            
            // Constrain predicted coordinates within the field
            predictedX = Math.max(18, Math.min(battleFieldWidth - 18, predictedX));
            predictedY = Math.max(18, Math.min(battleFieldHeight - 18, predictedY));
        }
        
        double aimAngle = Utils.normalAbsoluteAngle(Math.atan2(predictedX - getX(), predictedY - getY()));
        setTurnGunRightRadians(Utils.normalRelativeAngle(aimAngle - getGunHeadingRadians()));

        // Fire if gun is close to target alignment
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(bulletPower);
        }

        // Movement: Circular Orbiting & Random evasion
        // We stay roughly perpendicular to the target, reversing direction dynamically
        setTurnRightRadians(Utils.normalRelativeAngle(absoluteBearing - getHeadingRadians() + Math.PI/2 - (0.5 * moveDirection)));
        
        // Dynamic distance control
        if (e.getDistance() > 350) {
            setAhead(100 * moveDirection);
        } else if (e.getDistance() < 150) {
            setAhead(-100 * moveDirection);
        } else {
            setAhead(70 * moveDirection);
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Change direction and shift angle upon getting hit to throw off enemy targeting
        moveDirection = -moveDirection;
    }

    public void onHitWall(HitWallEvent e) {
        // Reverse direction upon hitting wall
        moveDirection = -moveDirection;
        setAhead(100 * moveDirection);
    }
}
