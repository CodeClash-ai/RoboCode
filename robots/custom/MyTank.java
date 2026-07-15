package custom;

import robocode.*;
import robocode.util.Utils;
import java.awt.geom.*;
import java.util.*;

public class MyTank extends AdvancedRobot {
    private static final double BULLET_POWER = 1.9;
    private static final double FIELD_WIDTH = 800;
    private static final double FIELD_HEIGHT = 600;
    private static final double WALL_MARGIN = 36;

    private static final GFTargeting targeting = new GFTargeting();
    private static double lastEnemyEnergy = 100.0;
    private static final Point2D.Double enemyLocation = new Point2D.Double();
    private static final Point2D.Double myLocation = new Point2D.Double();
    private static double enemyAbsoluteBearing = 0;
    private static double enemyVelocity = 0;
    private static double enemyHeading = 0;
    private static int direction = 1;
    
    private static final List<EnemyWave> waves = new ArrayList<>();
    private static final List<MyWave> myWaves = new ArrayList<>();

    @Override
    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        waves.clear();
        myWaves.clear();

        while (true) {
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        myLocation.setLocation(getX(), getY());
        enemyAbsoluteBearing = getHeadingRadians() + e.getBearingRadians();
        enemyLocation.setLocation(
            getX() + e.getDistance() * Math.sin(enemyAbsoluteBearing),
            getY() + e.getDistance() * Math.cos(enemyAbsoluteBearing)
        );
        enemyVelocity = e.getVelocity();
        enemyHeading = e.getHeadingRadians();

        // 1. WAVE SURFING (Movement)
        double changeInEnergy = lastEnemyEnergy - e.getEnergy();
        if (changeInEnergy >= 0.1 && changeInEnergy <= 3.0) {
            // Enemy fired a bullet! Create wave.
            EnemyWave wave = new EnemyWave();
            wave.fireTime = getTime() - 1;
            wave.bulletSpeed = 20.0 - 3.0 * changeInEnergy;
            wave.directAngle = enemyAbsoluteBearing + Math.PI; // wave comes from enemy
            wave.origin = new Point2D.Double(enemyLocation.x, enemyLocation.y);
            wave.direction = (enemyVelocity * Math.sin(enemyHeading - enemyAbsoluteBearing) >= 0) ? 1 : -1;
            waves.add(wave);
        }
        lastEnemyEnergy = e.getEnergy();

        updateWaves();
        updateMyWaves();
        doMovement();

        // 2. GUESS FACTOR TARGETING (Gun)
        double bulletPower = BULLET_POWER;
        if (e.getEnergy() < 4) {
            bulletPower = Math.max(0.1, e.getEnergy() / 4.0);
        }
        double bulletSpeed = 20.0 - 3.0 * bulletPower;
        double maxEscapeAngle = Math.asin(8.0 / bulletSpeed);
        
        double targetGFAngle = targeting.getBestAngle(e.getDistance(), enemyVelocity, maxEscapeAngle);
        
        // Find the direction of enemy rotation around us
        double enemyLateralVelocity = enemyVelocity * Math.sin(enemyHeading - enemyAbsoluteBearing);
        int enemyDirection = (enemyLateralVelocity >= 0) ? 1 : -1;
        
        double fireAngle = enemyAbsoluteBearing + enemyDirection * targetGFAngle;
        
        setTurnGunRightRadians(Utils.normalRelativeAngle(fireAngle - getGunHeadingRadians()));
        
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            Bullet b = setFireBullet(bulletPower);
            if (b != null) {
                MyWave w = new MyWave();
                w.origin = new Point2D.Double(getX(), getY());
                w.fireTime = getTime();
                w.bulletSpeed = bulletSpeed;
                w.directAngle = enemyAbsoluteBearing;
                w.maxEscapeAngle = maxEscapeAngle;
                w.direction = enemyDirection;
                w.stats = targeting.getStatsSegment(e.getDistance(), enemyVelocity);
                myWaves.add(w);
            }
        }

        // Radar lock
        setTurnRadarRightRadians(Utils.normalRelativeAngle(enemyAbsoluteBearing - getRadarHeadingRadians()) * 1.5);
    }

    private void updateWaves() {
        double time = getTime();
        for (int i = 0; i < waves.size(); i++) {
            EnemyWave w = waves.get(i);
            w.distanceTraveled = (time - w.fireTime) * w.bulletSpeed;
            if (w.distanceTraveled > myLocation.distance(w.origin) + 50) {
                waves.remove(i);
                i--;
            }
        }
    }

    private void updateMyWaves() {
        double time = getTime();
        for (int i = 0; i < myWaves.size(); i++) {
            MyWave w = myWaves.get(i);
            double distanceTraveled = (time - w.fireTime) * w.bulletSpeed;
            
            if (distanceTraveled >= w.origin.distance(enemyLocation)) {
                double currentAngle = Utils.normalAbsoluteAngle(Math.atan2(enemyLocation.x - w.origin.x, enemyLocation.y - w.origin.y));
                double angleDiff = Utils.normalRelativeAngle(currentAngle - w.directAngle);
                double gf = angleDiff / w.maxEscapeAngle;
                
                int bin = (int) Math.round((gf * w.direction + 1.0) * (GFTargeting.BINS / 2));
                bin = Math.max(0, Math.min(GFTargeting.BINS - 1, bin));
                
                for (int b = 0; b < GFTargeting.BINS; b++) {
                    w.stats[b] += 1.0 / (1.0 + Math.pow(b - bin, 2));
                }
                
                myWaves.remove(i);
                i--;
            }
        }
    }

    private void doMovement() {
        if (waves.isEmpty()) {
            if (Math.random() < 0.05) {
                direction = -direction;
            }
            double targetAngle = enemyAbsoluteBearing + Math.PI / 2 + 0.3 * direction;
            Point2D.Double targetLoc = predictPosition(targetAngle, 120);
            goTo(targetLoc);
            return;
        }

        EnemyWave surfWave = getClosestWave();
        if (surfWave == null) return;

        double[] playAngles = { -0.5, -0.25, 0, 0.25, 0.5 };
        double bestDanger = Double.POSITIVE_INFINITY;
        Point2D.Double bestLoc = null;

        for (double offset : playAngles) {
            double angle = enemyAbsoluteBearing + Math.PI / 2 + offset * direction;
            Point2D.Double testLoc = predictPosition(angle, 100);
            double danger = getDanger(testLoc, surfWave);
            if (danger < bestDanger) {
                bestDanger = danger;
                bestLoc = testLoc;
            }
        }

        if (bestLoc != null) {
            goTo(bestLoc);
        }
    }

    private EnemyWave getClosestWave() {
        double minDistance = Double.MAX_VALUE;
        EnemyWave closest = null;
        for (EnemyWave w : waves) {
            double dist = myLocation.distance(w.origin) - w.distanceTraveled;
            if (dist > 0 && dist < minDistance) {
                minDistance = dist;
                closest = w;
            }
        }
        return closest;
    }

    private double getDanger(Point2D.Double loc, EnemyWave wave) {
        double distToEnemy = loc.distance(enemyLocation);
        double distDanger = 0;
        if (distToEnemy < 150) distDanger = 50.0 / distToEnemy;
        if (distToEnemy > 600) distDanger = distToEnemy / 12.0;

        double angle = Utils.normalAbsoluteAngle(Math.atan2(loc.x - wave.origin.x, loc.y - wave.origin.y));
        double angleDiff = Utils.normalRelativeAngle(angle - wave.directAngle);
        double gf = angleDiff / Math.asin(8.0 / wave.bulletSpeed);
        
        return distDanger + Math.abs(gf) * 10.0;
    }

    private Point2D.Double predictPosition(double angle, double distance) {
        double targetX = myLocation.x + distance * Math.sin(angle);
        double targetY = myLocation.y + distance * Math.cos(angle);
        
        targetX = Math.max(WALL_MARGIN, Math.min(FIELD_WIDTH - WALL_MARGIN, targetX));
        targetY = Math.max(WALL_MARGIN, Math.min(FIELD_HEIGHT - WALL_MARGIN, targetY));
        
        return new Point2D.Double(targetX, targetY);
    }

    private void goTo(Point2D.Double target) {
        double angle = Utils.normalRelativeAngle(Math.atan2(target.x - getX(), target.y - getY()) - getHeadingRadians());
        double turnAngle = Math.atan(Math.tan(angle));
        
        setTurnRightRadians(turnAngle);
        if (angle == turnAngle) {
            setAhead(myLocation.distance(target));
        } else {
            setBack(myLocation.distance(target));
        }
    }

    private static class EnemyWave {
        Point2D.Double origin;
        double fireTime;
        double bulletSpeed;
        double directAngle;
        double distanceTraveled;
        int direction;
    }

    private static class MyWave {
        Point2D.Double origin;
        double fireTime;
        double bulletSpeed;
        double directAngle;
        double maxEscapeAngle;
        int direction;
        double[] stats;
    }

    private static class GFTargeting {
        private static final int BINS = 31;
        private static final int DISTANCE_SEGMENTS = 4;
        private static final int VELOCITY_SEGMENTS = 4;
        private final double[][][] stats = new double[DISTANCE_SEGMENTS][VELOCITY_SEGMENTS][BINS];

        public double[] getStatsSegment(double distance, double velocity) {
            int distIdx = (int) (distance / 200.0);
            if (distIdx >= DISTANCE_SEGMENTS) distIdx = DISTANCE_SEGMENTS - 1;

            int velIdx = (int) (Math.abs(velocity) / 2.0);
            if (velIdx >= VELOCITY_SEGMENTS) velIdx = VELOCITY_SEGMENTS - 1;

            return stats[distIdx][velIdx];
        }

        public double getBestAngle(double distance, double velocity, double maxEscapeAngle) {
            double[] segmentStats = getStatsSegment(distance, velocity);
            int bestBin = BINS / 2;
            for (int i = 0; i < BINS; i++) {
                if (segmentStats[i] > segmentStats[bestBin]) {
                    bestBin = i;
                }
            }
            return maxEscapeAngle * ((double) (bestBin - BINS / 2) / (BINS / 2));
        }
    }
}
