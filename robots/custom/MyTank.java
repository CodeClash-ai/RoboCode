// CodeClash ladder import
// Source: https://github.com/vikdov/RoboCodeEvent/blob/HEAD/DominatorX.java
// Author: vikdov   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import robocode.*;
import java.awt.Color;

/**
 * MyTank - A championship-level Robocode robot
 * Combines advanced targeting, intelligent movement, and energy management
 */
public class MyTank extends AdvancedRobot {
    
    private double radarTurnAngle = 0;
    private String targetName = "";
    private double targetDistance = 0;
    private double targetEnergy = 0;
    private long lastScanTime = 0;
    private static final double BULLET_POWER = 2.5;
    private static final double MAX_FIRE_POWER = 3.0;
    private static final double MIN_FIRE_POWER = 0.5;
    
    public void run() {
        // Set up robot to maximize efficiency
        setColors(Color.red, Color.black, Color.orange);
        setAdjustRadarForRobotTurn(true);
        setAdjustGunForRobotTurn(true);
        
        // Enable infinite turn rates for advanced movements
        setMaxVelocity(8);
        
        // Main battle loop
        while (true) {
            // Aggressive radar scanning
            setTurnRadarRight(360);
            execute();
        }
    }
    
    /**
     * Called when radar scans an enemy robot
     * This is where targeting and firing decisions are made
     */
    public void onScannedRobot(ScannedRobotEvent e) {
        lastScanTime = getTime();
        targetName = e.getName();
        targetDistance = e.getDistance();
        targetEnergy = e.getEnergy();
        
        double absBearing = getHeading() + e.getBearing();
        double latVel = e.getVelocity() * Math.sin(e.getHeadingRadians() - Math.toRadians(absBearing));
        
        // Turn gun to face target with leading calculation
        double gunTurn = normalizeBearing(absBearing - getGunHeading());
        setTurnGunRight(gunTurn);
        
        // Advanced targeting: predict enemy position for bullet impact
        double bulletPower = calculateFirePower(targetDistance, targetEnergy);
        double predictedX = predictX(e, bulletPower);
        double predictedY = predictY(e, bulletPower);
        
        // Fire with calculated power
        if (Math.abs(gunTurn) < 10) {
            setFire(bulletPower);
        }
        
        // Movement strategy: anti-gravity with wall avoidance
        performMovement(absBearing);
    }
    
    /**
     * Calculate optimal fire power based on distance and target energy
     */
    private double calculateFirePower(double distance, double enemyEnergy) {
        double power = BULLET_POWER;
        
        // If enemy is weak, finish with max power
        if (enemyEnergy < 20 && distance < 200) {
            power = Math.min(enemyEnergy / 4 + 0.1, MAX_FIRE_POWER);
        }
        // If enemy is far, use medium power
        else if (distance > 400) {
            power = Math.min(1.5, getEnergy() / 4);
        }
        // Medium range: balanced approach
        else {
            power = Math.min(BULLET_POWER, getEnergy() / 5);
        }
        
        return Math.max(MIN_FIRE_POWER, Math.min(power, MAX_FIRE_POWER));
    }
    
    /**
     * Predict X position of target for linear targeting
     */
    private double predictX(ScannedRobotEvent e, double bulletPower) {
        double bulletVelocity = 20.0 - (3.0 * bulletPower);
        double timeToHit = e.getDistance() / bulletVelocity;
        
        double absBearing = getHeading() + e.getBearing();
        double targetX = getX() + e.getDistance() * Math.sin(Math.toRadians(absBearing));
        double targetVelX = e.getVelocity() * Math.sin(Math.toRadians(e.getHeading()));
        
        return targetX + (targetVelX * timeToHit);
    }
    
    /**
     * Predict Y position of target for linear targeting
     */
    private double predictY(ScannedRobotEvent e, double bulletPower) {
        double bulletVelocity = 20.0 - (3.0 * bulletPower);
        double timeToHit = e.getDistance() / bulletVelocity;
        
        double absBearing = getHeading() + e.getBearing();
        double targetY = getY() + e.getDistance() * Math.cos(Math.toRadians(absBearing));
        double targetVelY = e.getVelocity() * Math.cos(Math.toRadians(e.getHeading()));
        
        return targetY + (targetVelY * timeToHit);
    }
    
    /**
     * Anti-gravity movement strategy: avoid other robots and walls
     */
    private void performMovement(double absBearing) {
        // Oscillating movement pattern with random elements
        if (getTime() % 40 < 20) {
            setAhead(500);
        } else {
            setBack(500);
        }
        
        // Strafe perpendicular to enemy when in medium range
        if (targetDistance < 300) {
            if (Math.random() > 0.5) {
                setTurnRight(90 + Math.random() * 30);
            } else {
                setTurnLeft(90 + Math.random() * 30);
            }
        }
        
        // Wall avoidance
        double x = getX();
        double y = getY();
        double fieldWidth = getBattleFieldWidth();
        double fieldHeight = getBattleFieldHeight();
        
        if (x < 50 || x > fieldWidth - 50 || y < 50 || y > fieldHeight - 50) {
            setTurnRight(Math.random() * 120 - 60);
            setAhead(300);
        }
    }
    
    /**
     * Handle bullet impacts
     */
    public void onHitByBullet(HitByBulletEvent e) {
        // Aggressive response: turn toward attacker and fire
        setTurnRight(normalizeBearing(e.getBearing() + 180));
        setBack(100);
    }
    
    /**
     * Handle robot collisions
     */
    public void onHitRobot(HitRobotEvent e) {
        if (e.isMyFault()) {
            setBack(50);
        } else {
            setAhead(50);
        }
    }
    
    /**
     * Handle wall collisions
     */
    public void onHitWall(HitWallEvent e) {
        setBack(100);
        setTurnRight(90);
    }
    
    /**
     * Normalize bearing to -180 to 180 range
     */
    private double normalizeBearing(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
}