package custom;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

public class MyTank extends AdvancedRobot {

    private double lastEnemyHeading = 0;
    private double lastEnemyEnergy = 100.0;
    private int moveDirection = 1;
    private int hitWallCooldown = 0;

    // Guess Factor Targeting Statistics
    // 2 distance segments (0-350, 350+), 31 bins each
    private static final int DISTANCE_SEGMENTS = 2;
    private static final int BINS = 31;
    private static final int MIDDLE_BIN = 15;
    private static final int[][] stats = new int[DISTANCE_SEGMENTS][BINS];

    // Track active waves
    private static final List<Wave> waves = new ArrayList<>();

    private static class Wave {
        double startX, startY;
        double targetBearing;
        double bulletSpeed;
        int direction;
        double maxEscapeAngle;
        long fireTime;
        int distanceSegment;

        public Wave(double startX, double startY, double targetBearing, double bulletSpeed, int direction, double maxEscapeAngle, long fireTime, int distanceSegment) {
            this.startX = startX;
            this.startY = startY;
            this.targetBearing = targetBearing;
            this.bulletSpeed = bulletSpeed;
            this.direction = direction;
            this.maxEscapeAngle = maxEscapeAngle;
            this.fireTime = fireTime;
            this.distanceSegment = distanceSegment;
        }
    }

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

        // Determine distance segment
        int distSeg = e.getDistance() < 350 ? 0 : 1;

        // Update waves
        double enemyX = getX() + e.getDistance() * Math.sin(absoluteBearing);
        double enemyY = getY() + e.getDistance() * Math.cos(absoluteBearing);

        for (int i = 0; i < waves.size(); i++) {
            Wave w = waves.get(i);
            double traveled = (getTime() - w.fireTime) * w.bulletSpeed;
            double distToWaveSource = Point2D.distance(w.startX, w.startY, enemyX, enemyY);
            if (traveled >= distToWaveSource) {
                double currentBearing = Math.atan2(enemyX - w.startX, enemyY - w.startY);
                double angleDiff = Utils.normalRelativeAngle(currentBearing - w.targetBearing);
                double guessFactor = angleDiff / w.maxEscapeAngle * w.direction;
                int bin = (int) Math.round((guessFactor + 1.0) * MIDDLE_BIN);
                bin = Math.max(0, Math.min(BINS - 1, bin));
                stats[w.distanceSegment][bin]++;
                waves.remove(i);
                i--;
            }
        }

        // Select the best guess factor from stats
        int bestBin = MIDDLE_BIN;
        int maxHits = -1;
        for (int b = 0; b < BINS; b++) {
            if (stats[distSeg][b] > maxHits) {
                maxHits = stats[distSeg][b];
                bestBin = b;
            }
        }

        double gunTurn;
        // If we don't have enough statistics yet, fall back to simple circular/linear prediction
        if (maxHits <= 1) {
            double enemyHeading = e.getHeadingRadians();
            double enemyVelocity = e.getVelocity();
            double headingChange = enemyHeading - lastEnemyHeading;
            
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
            gunTurn = Utils.normalRelativeAngle(Math.atan2(predictedX - getX(), predictedY - getY()) - getGunHeadingRadians());
        } else {
            double guessFactor = ((double) bestBin / MIDDLE_BIN) - 1.0;
            double enemyHeading = e.getHeadingRadians();
            double enemyVelocity = e.getVelocity();
            int direction = 1;
            if (enemyVelocity != 0) {
                double lateralVelocity = enemyVelocity * Math.sin(enemyHeading - absoluteBearing);
                if (lateralVelocity < 0) {
                    direction = -1;
                }
            }
            double maxEscapeAngle = Math.asin(8.0 / bulletSpeed);
            double targetAngle = absoluteBearing + direction * guessFactor * maxEscapeAngle;
            gunTurn = Utils.normalRelativeAngle(targetAngle - getGunHeadingRadians());
        }

        setTurnGunRightRadians(gunTurn);

        // Fire only when gun is cool and alignment is highly precise
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            Bullet b = setFireBullet(bulletPower);
            if (b != null) {
                double enemyHeading = e.getHeadingRadians();
                double enemyVelocity = e.getVelocity();
                int direction = 1;
                if (enemyVelocity != 0) {
                    double lateralVelocity = enemyVelocity * Math.sin(enemyHeading - absoluteBearing);
                    if (lateralVelocity < 0) {
                        direction = -1;
                    }
                }
                double maxEscapeAngle = Math.asin(8.0 / bulletSpeed);
                Wave w = new Wave(getX(), getY(), absoluteBearing, bulletSpeed, direction, maxEscapeAngle, getTime(), distSeg);
                waves.add(w);
            }
        }

        // Jittering movement parameters
        // Periodically randomize max velocity to disrupt enemy's linear/circular targeting
        if (getTime() % 15 == 0) {
            setMaxVelocity(4.0 + Math.random() * 4.0); // Velocity between 4 and 8
        }
        if (getTime() % 35 == 0) {
            if (Math.random() < 0.4) {
                moveDirection = -moveDirection;
            }
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
        setAhead(150 * moveDirection);

        lastEnemyHeading = e.getHeadingRadians();
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
        setAhead(150 * moveDirection);
    }
    
    public void onHitRobot(HitRobotEvent e) {
        if (e.getEnergy() < getEnergy()) {
            // Push aggressively through weaker targets
            setAhead(100);
        } else {
            // Evade superior physical collisions
            moveDirection = -moveDirection;
            setAhead(150 * moveDirection);
        }
    }
}
