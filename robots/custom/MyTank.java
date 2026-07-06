// CodeClash ladder import
// Source: https://github.com/zhiwei121/robocode-hero/blob/HEAD/Hero.java
// Author: zhiwei121   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobotDeathEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

/**
 * MyTank - Pattern Matching Bot
 * 
 * Reference: SLTeam (pattern matching gun)
 * Adapted & improved for single robot use.
 */
public class MyTank extends AdvancedRobot {

    // === Initial State ===
    static Boolean hithithit = false;
    enemyState enemy = new enemyState();

    // === Pattern Match ===
    private static final int MAX_PATTERN_LENGTH = 30;
    private static Map<String, int[]> matcher = new HashMap<String, int[]>(40000);
    private static String enemyHistory;

    // === Fire ===
    private static double FIRE_POWER = 3.0;
    private static double FIRE_SPEED = Rules.getBulletSpeed(FIRE_POWER);

    // === Predictions ===
    private static List<Point2D.Double> predictions = new ArrayList<Point2D.Double>();

    // === Movement ===
    static final double BASE_MOVEMENT = 180;
    static final double BASE_TURN = Math.PI / 1.5;
    static double movement;

    // ──────────────────────────────────────────
    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // Black theme
        setBodyColor(Color.BLACK);
        setGunColor(new Color(40, 40, 40));
        setRadarColor(new Color(20, 20, 20));
        setBulletColor(Color.RED);
        setScanColor(Color.ORANGE);

        enemyHistory = "";
        movement = Double.POSITIVE_INFINITY;

        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

        do {
            scan();
            if (getDistanceRemaining() == 0) {
                setAhead(movement = -movement);
                setTurnRightRadians(BASE_TURN);
                hithithit = false;
            }
        } while (true);
    }

    // ──────────────────────────────────────────
    // EVENT HANDLERS
    // ──────────────────────────────────────────

    public void onHitWall(HitWallEvent e) {
        if (Math.abs(movement) > BASE_MOVEMENT) {
            movement = BASE_MOVEMENT;
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
    }

    public void onHitRobot(HitRobotEvent e) {
        if (hithithit == false) {
            double absoluteBearing = e.getBearingRadians() + getHeadingRadians();
            turnRadarRightRadians(Utils.normalRelativeAngle(absoluteBearing - getRadarHeadingRadians()));
            hithithit = true;
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // Update enemy state
        enemy.update(e, this);

        // Fire when gun is ready and we have energy
        if (getGunTurnRemaining() == 0 && getEnergy() > 1) {
            smartFire();
        }

        // Track enemy with radar
        trackHim();

        // Record pattern only for valid steps
        if (enemy.thisStep == -1) {
            return;
        }
        record(enemy.thisStep);
        enemyHistory = (char) enemy.thisStep + enemyHistory;

        // Predict enemy positions for gun aiming
        predictions.clear();
        Point2D.Double myP = new Point2D.Double(getX(), getY());
        Point2D.Double enemyP = project(myP, enemy.absoluteBearing, e.getDistance());

        String pattern = enemyHistory;
        for (double d = 0; d < myP.distance(enemyP); d += FIRE_SPEED) {
            int nextStep = predict(pattern);
            enemy.decode(nextStep);
            enemyP = project(enemyP, enemy.headingRadian, enemy.velocity);
            predictions.add(enemyP);
            pattern = (char) nextStep + pattern;
        }

        // Aim gun at predicted position
        enemy.absoluteBearing = Math.atan2(enemyP.x - myP.x, enemyP.y - myP.y);
        double gunTurn = Utils.normalRelativeAngle(enemy.absoluteBearing - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);
    }

    // ──────────────────────────────────────────
    // MY FUNCTIONS
    // ──────────────────────────────────────────

    public void smartFire() {
        FIRE_POWER = Math.min(
            Math.min(getEnergy() / 6.0, 1000.0 / enemy.distance),
            enemy.energy / 3.0
        );
        // Ensure minimum fire power
        if (FIRE_POWER < 0.1) FIRE_POWER = 0.1;
        FIRE_SPEED = Rules.getBulletSpeed(FIRE_POWER);
        setFire(FIRE_POWER);
    }

    public void trackHim() {
        double radarOffset = Utils.normalRelativeAngle(
            enemy.absoluteBearing - getRadarHeadingRadians()
        );
        setTurnRadarRightRadians(radarOffset * 1.2);
    }

    /**
     * Record the current (dh, v) state into pattern frequency tables.
     */
    private void record(int thisStep) {
        int maxLength = Math.min(MAX_PATTERN_LENGTH, enemyHistory.length());
        for (int i = 0; i <= maxLength; ++i) {
            String pattern = enemyHistory.substring(0, i);
            int[] frequencies = matcher.get(pattern);
            if (frequencies == null) {
                // 21 possible dh values × 17 possible v values
                frequencies = new int[21 * 17];
                matcher.put(pattern, frequencies);
            }
            ++frequencies[thisStep];
        }
    }

    /**
     * Predict next enemy state by looking up the longest matching pattern.
     */
    private int predict(String pattern) {
        int[] frequencies = null;
        for (int patternLength = Math.min(pattern.length(), MAX_PATTERN_LENGTH);
             frequencies == null && patternLength > 0;
             --patternLength) {
            frequencies = matcher.get(pattern.substring(0, patternLength));
        }
        int nextTick = 0;
        for (int i = 1; i < frequencies.length; ++i) {
            if (frequencies[nextTick] < frequencies[i]) {
                nextTick = i;
            }
        }
        return nextTick;
    }

    /**
     * Project a point by (distance) along (angle) from (p).
     */
    private static Point2D.Double project(Point2D.Double p, double angle, double distance) {
        double x = p.x + distance * Math.sin(angle);
        double y = p.y + distance * Math.cos(angle);
        return new Point2D.Double(x, y);
    }
}

// ──────────────────────────────────────────────
// ENEMY STATE CLASS
// Encodes decodes (delta-heading, velocity)
// pairs into a single integer symbol for
// pattern matching.
// ──────────────────────────────────────────────
class enemyState {
    public double headingRadian = 0.0;
    public double bearingRadian = 0.0;
    public double distance = 0.0;
    public double absoluteBearing = 0.0;
    public double x = 0.0;
    public double y = 0.0;
    public double velocity = 0.0;
    public double energy = 100.0;

    // Addition: for pattern matching
    public double lastEnemyHeading = 0;
    public int thisStep = 0;

    /**
     * Encode (delta heading, velocity) into a symbol.
     * @return symbol [0..356] or -1 if turn rate exceeds maximum.
     */
    public static int encode(double dh, double v) {
        if (Math.abs(dh) > Rules.MAX_TURN_RATE_RADIANS) {
            return -1;
        }
        // dh: -10..10 degrees  → +10 offset → 0..20
        // v : -8..8            → +8  offset → 0..16
        int dhCode = (int) Math.rint(Math.toDegrees(dh)) + 10;
        int vCode  = (int) Math.rint(v + 8);
        return 17 * dhCode + vCode;
    }

    /**
     * Decode a symbol back into delta heading and velocity,
     * and accumulate onto headingRadian / velocity.
     */
    public void decode(int symbol) {
        headingRadian += Math.toRadians(symbol / 17 - 10);
        velocity = symbol % 17 - 8;
    }

    /**
     * Pull latest scan data from event.
     */
    public void update(ScannedRobotEvent e, AdvancedRobot me) {
        headingRadian   = e.getHeadingRadians();
        bearingRadian   = e.getBearingRadians();
        distance        = e.getDistance();
        absoluteBearing = bearingRadian + me.getHeadingRadians();
        x               = me.getX() + Math.sin(absoluteBearing) * distance;
        y               = me.getY() + Math.cos(absoluteBearing) * distance;
        velocity        = e.getVelocity();
        energy          = e.getEnergy();

        thisStep = encode(headingRadian - lastEnemyHeading, velocity);
        lastEnemyHeading = headingRadian;
    }
}
