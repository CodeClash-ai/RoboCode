package custom;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.*;

public class MyTank extends AdvancedRobot {

    private double lastEnemyHeading = 0;
    private double lastEnemyEnergy = 100.0;
    private int moveDirection = 1;
    private int hitWallCooldown = 0;

    public void run() {
        // Aesthetic setup matching our superior tech
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
        // Infinite radar lock with small overshoot to prevent slipping
        double absoluteBearing = getHeadingRadians() + e.getBearingRadians();
        double radarTurn = absoluteBearing - getRadarHeadingRadians();
        setTurnRadarRightRadians(Utils.normalRelativeAngle(radarTurn) * 1.5);

        // Advanced Energy Drop Detection for Evasive Dodging
        double energyDrop = lastEnemyEnergy - e.getEnergy();
        if (energyDrop >= 0.1 && energyDrop <= 3.0) {
            // Unpredictable dodge: change direction or alter pacing when shot at
            if (Math.random() < 0.6) {
                moveDirection = -moveDirection;
            }
        }
        lastEnemyEnergy = e.getEnergy();

        // Dynamic bullet power selection to maximize damage and conserve energy
        double bulletPower = 3.0;
        if (e.getDistance() > 400) {
            bulletPower = 1.5;
        } else if (e.getDistance() > 200) {
            bulletPower = 2.0;
        }
        // Protect ourselves from self-defeat via over-firing when low on energy
        bulletPower = Math.min(bulletPower, getEnergy() / 6.0);
        if (bulletPower < 0.1) bulletPower = 0.1;
        
        double bulletSpeed = 20 - 3 * bulletPower;

        // Linear and Circular Predictive Target Engine
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
            
            // Constrain predictions to the battlefield boundaries minus a safe margin
            double margin = 18.0;
            predictedX = Math.max(margin, Math.min(getBattleFieldWidth() - margin, predictedX));
            predictedY = Math.max(margin, Math.min(getBattleFieldHeight() - margin, predictedY));
        }
        
        double gunTurn = Utils.normalRelativeAngle(Math.atan2(predictedX - getX(), predictedY - getY()) - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        // Fire only when gun is cool and alignment is highly precise
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(bulletPower);
        }

        // Target spacing logic: Maintain perpendicular orbit at preferred distance
        double preferredDistance = 350.0;
        double approachAngle = 0.0;
        if (e.getDistance() > preferredDistance + 50) {
            approachAngle = 0.3 * moveDirection; 
        } else if (e.getDistance() < preferredDistance - 50) {
            approachAngle = -0.3 * moveDirection;
        }
        double targetAngle = absoluteBearing + Math.PI / 2 + approachAngle;
        
        // Active wall smoothing & boundary check
        // We use a larger wall margin and smooth the movement vector rather than just changing moveDirection
        double wallMargin = 60.0;
        double width = getBattleFieldWidth();
        double height = getBattleFieldHeight();
        double currentX = getX();
        double currentY = getY();
        
        // Push the target angle away from the walls
        double testX = currentX + 120 * Math.sin(targetAngle);
        double testY = currentY + 120 * Math.cos(targetAngle);
        
        if (testX < wallMargin) {
            targetAngle += (moveDirection > 0 ? 0.5 : -0.5);
        } else if (testX > width - wallMargin) {
            targetAngle -= (moveDirection > 0 ? 0.5 : -0.5);
        }
        
        if (testY < wallMargin) {
            targetAngle -= (moveDirection > 0 ? 0.5 : -0.5) * Math.signum(Math.sin(targetAngle));
        } else if (testY > height - wallMargin) {
            targetAngle += (moveDirection > 0 ? 0.5 : -0.5) * Math.signum(Math.sin(targetAngle));
        }

        if (hitWallCooldown > 0) {
            hitWallCooldown--;
        }

        // Second level safety: if we are still going to hit the wall or get too close, reverse direction immediately
        double nextX = currentX + 60 * Math.sin(targetAngle) * moveDirection;
        double nextY = currentY + 60 * Math.cos(targetAngle) * moveDirection;
        if (nextX < 40.0 || nextX > width - 40.0 || nextY < 40.0 || nextY > height - 40.0) {
            if (hitWallCooldown == 0) {
                moveDirection = -moveDirection;
                hitWallCooldown = 8;
            }
        }
        
        setTurnRightRadians(Utils.normalRelativeAngle(targetAngle - getHeadingRadians()));
        setAhead(100 * moveDirection);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Change movement direction upon taking damage to disrupt enemy's targeting profile
        moveDirection = -moveDirection;
    }

    public void onHitWall(HitWallEvent e) {
        if (hitWallCooldown == 0) {
            moveDirection = -moveDirection;
            hitWallCooldown = 15;
        }
        setAhead(100 * moveDirection);
    }
    
    public void onHitRobot(HitRobotEvent e) {
        if (e.getEnergy() < getEnergy()) {
            // Push aggressively through weaker targets
            setAhead(100);
        } else {
            // Evade superior physical collisions
            moveDirection = -moveDirection;
            setAhead(100 * moveDirection);
        }
    }
}
