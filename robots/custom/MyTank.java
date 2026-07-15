package custom;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.*;

public class MyTank extends AdvancedRobot {

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

        // Keep gun pointed at the scanned robot with some predictive leading (simple linear lead)
        double bulletPower = Math.min(3.0, getEnergy() / 10.0);
        if (bulletPower < 0.1) bulletPower = 0.1;
        double bulletSpeed = 20 - 3 * bulletPower;

        double absoluteBearing = getHeadingRadians() + e.getBearingRadians();
        double enemyX = getX() + e.getDistance() * Math.sin(absoluteBearing);
        double enemyY = getY() + e.getDistance() * Math.cos(absoluteBearing);
        
        double enemyHeading = e.getHeadingRadians();
        double enemyVelocity = e.getVelocity();
        
        // Simple predictive aim
        double deltaTime = e.getDistance() / bulletSpeed;
        double predictedX = enemyX + enemyVelocity * Math.sin(enemyHeading) * deltaTime;
        double predictedY = enemyY + enemyVelocity * Math.cos(enemyHeading) * deltaTime;
        
        // Clip predicted position to the battlefield boundaries to avoid aiming past walls
        double margin = 18.0;
        predictedX = Math.max(margin, Math.min(getBattleFieldWidth() - margin, predictedX));
        predictedY = Math.max(margin, Math.min(getBattleFieldHeight() - margin, predictedY));
        
        double gunTurn = Utils.normalRelativeAngle(Math.atan2(predictedX - getX(), predictedY - getY()) - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        // Fire if gun is reasonably aligned and cool
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(bulletPower);
        }

        // Avoid being a stationary target or stuck seesaw: move in a circle or perpendicular to the enemy
        // Circular/perpendicular movement relative to enemy
        double moveDirection = 1;
        if (e.getDistance() < 150) {
            moveDirection = -1; // back away if too close
        }
        
        setTurnRightRadians(Utils.normalRelativeAngle(e.getBearingRadians() + Math.PI/2 - (Math.PI/6 * moveDirection)));
        setAhead(150 * moveDirection);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Change direction on hit to throw off enemy targeting
        setAhead(-200 * Math.signum(getVelocity()));
    }

    public void onHitWall(HitWallEvent e) {
        // Back off the wall
        setAhead(-150 * Math.signum(getVelocity()));
        setTurnRight(45);
    }
    
    public void onHitRobot(HitRobotEvent e) {
        // Ram or back off depending on relative energy
        if (e.getEnergy() < getEnergy()) {
            setAhead(100);
        } else {
            setAhead(-100);
        }
    }
}
