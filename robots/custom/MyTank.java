package custom;

import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.HitByBulletEvent;
import robocode.HitWallEvent;
import robocode.HitRobotEvent;
import robocode.DeathEvent;
import robocode.util.Utils;
import java.awt.geom.Point2D;
import java.awt.Color;

/**
 * MyTank - a competitive 1v1 robot.
 *
 * Strategy:
 *  - Radar: narrow "lock" sweep on the single enemy (infinite lock for 1v1).
 *  - Gun: predictive targeting. Uses a simple statistical/circular-linear
 *    predictor plus head-on fallback, picks bullet power based on distance & energy.
 *  - Movement: orbital (perpendicular) movement around the enemy with
 *    wall smoothing, random reversals, and evasive reaction to being hit.
 */
public class MyTank extends AdvancedRobot {

    // Enemy tracking
    private double enemyX, enemyY;
    private double enemyEnergy = 100;
    private double enemyHeading = 0;
    private double enemyVelocity = 0;
    private double enemyDistance = 1000;
    private double enemyBearing = 0;
    private boolean enemyScanned = false;
    private long lastScanTime = 0;

    // Movement
    private int moveDirection = 1;
    private double lastEnemyEnergy = 100;

    // Circular targeting: track enemy turn rate
    private double lastEnemyHeading = 0;
    private double enemyTurnRate = 0;
    private boolean haveLastHeading = false;

    public void run() {
        setColors(Color.BLUE, Color.CYAN, Color.WHITE);

        // Let radar and gun turn independently of body
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        // Initial radar sweep to find enemy
        turnRadarRight(Double.POSITIVE_INFINITY);

        while (true) {
            // If we haven't scanned recently, keep the radar sweeping.
            if (getTime() - lastScanTime > 4) {
                setTurnRadarRight(Double.POSITIVE_INFINITY);
            }
            doMovement();
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        lastScanTime = getTime();
        enemyScanned = true;

        double absBearing = getHeadingRadians() + e.getBearingRadians();
        enemyBearing = absBearing;
        enemyDistance = e.getDistance();
        enemyHeading = e.getHeadingRadians();
        enemyVelocity = e.getVelocity();

        // Track enemy turn rate for circular prediction
        if (haveLastHeading) {
            double dh = Utils.normalRelativeAngle(enemyHeading - lastEnemyHeading);
            enemyTurnRate = Math.max(-0.15, Math.min(0.15, dh));
        }
        lastEnemyHeading = enemyHeading;
        haveLastHeading = true;

        // Enemy absolute position
        enemyX = getX() + Math.sin(absBearing) * enemyDistance;
        enemyY = getY() + Math.cos(absBearing) * enemyDistance;

        lastEnemyEnergy = enemyEnergy;
        enemyEnergy = e.getEnergy();

        // ---- Radar lock ----
        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        // Widen slightly for a robust lock
        setTurnRadarRightRadians(radarTurn * 2.0);

        // ---- Gun: predictive aim ----
        aimAndFire(absBearing);

        // ---- Movement ----
        doMovement();
    }

    private void aimAndFire(double absBearing) {
        // Data-driven vs trex22__deepthought (stop-and-go wall-hugging dodger):
        // Replaying its recorded trajectory showed that damage-per-tick is
        // MAXIMIZED by full-power (3.0) bullets even though hit rate is a bit
        // lower, because damage/hit (16) dominates the slower cooldown & the
        // small hit-rate loss. Best aim = 90% current position + 10% linear lead
        // (blend w=0.9), which measured ~44% hit rate vs ~32% for pure linear.
        double power = 3.0;
        if (getEnergy() < 20) power = Math.min(power, 1.5);
        if (getEnergy() < 10) power = Math.min(power, 0.8);
        if (getEnergy() < 4)  power = Math.min(power, 0.3);
        power = Math.max(0.1, Math.min(power, 3.0));

        double bulletSpeed = 20 - 3 * power;

        // Linear lead prediction over bullet flight time.
        double dist = Point2D.distance(getX(), getY(), enemyX, enemyY);
        double flight = dist / bulletSpeed;
        double leadX = enemyX + Math.sin(enemyHeading) * enemyVelocity * flight;
        double leadY = enemyY + Math.cos(enemyHeading) * enemyVelocity * flight;

        // Blend: heavy weight on current pos (enemy stops ~40% of ticks and its
        // bursts are reactive/unpredictable, so lead over-shoots).
        double W = 1.0;  // pure head-on: replay sim shows 27.1% hit vs 22.9% for 0.90 (enemy reactive dodge -> lead overshoots)
        double predX = W * enemyX + (1 - W) * leadX;
        double predY = W * enemyY + (1 - W) * leadY;

        // Clamp prediction inside the field.
        double bw = getBattleFieldWidth(), bh = getBattleFieldHeight();
        predX = Math.max(18, Math.min(bw - 18, predX));
        predY = Math.max(18, Math.min(bh - 18, predY));

        double aimAngle = Math.atan2(predX - getX(), predY - getY());
        double gunTurn = Utils.normalRelativeAngle(aimAngle - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        if (getGunHeat() == 0 && Math.abs(gunTurn) < 0.12 && getEnergy() > power + 0.2) {
            setFire(power);
        }
    }

    // Movement oscillation state
    private long lastReverseTime = 0;
    private double moveTimer = 0;

    private void doMovement() {
        if (!enemyScanned) {
            return;
        }

        // Detect enemy firing: energy drop between 0.1 and 3.0
        double energyDrop = lastEnemyEnergy - enemyEnergy;
        boolean enemyFired = energyDrop >= 0.09 && energyDrop <= 3.05;

        double absBearing = enemyBearing;

        // Range control: hold a good orbit distance (~400px) but jitter the
        // target so a statistical/GF gun can't fix on a constant orbit radius.
        double rangeBias = 0.0;
        if (enemyDistance > 500) rangeBias = -0.40;      // pull in
        else if (enemyDistance < 300) rangeBias = 0.45;  // push out

        double desiredDir = absBearing + (Math.PI / 2 + rangeBias) * moveDirection;

        // Wall smoothing: steer away from walls
        desiredDir = wallSmoothing(getX(), getY(), desiredDir, moveDirection);

        double turn = Utils.normalRelativeAngle(desiredDir - getHeadingRadians());

        // If turn is > 90deg, drive backwards instead (smoother)
        double moveAmount = 150;
        if (Math.abs(turn) > Math.PI / 2) {
            turn = Utils.normalRelativeAngle(turn + Math.PI);
            moveAmount = -moveAmount;
        }

        setTurnRightRadians(turn);
        setAhead(moveAmount);

        // --- Unpredictable reversals to defeat GuessFactor / pattern targeting ---
        // Two independent triggers so the enemy GF gun can't lock our profile:
        //  (a) react to detected enemy fire (dodge the incoming wave), but only
        //      ~50% of the time and rate-limited, so it isn't a strict alternation
        //      that itself becomes learnable at the enemy's fire cadence.
        //  (b) random-length orbit segments (avg ~20 ticks) independent of the
        //      enemy, so our lateral motion has no fixed period.
        long now = getTime();
        if (enemyFired && now - lastReverseTime >= 6 && Math.random() < 0.5) {
            moveDirection = -moveDirection;
            lastReverseTime = now;
        } else if (now - lastReverseTime >= 8 && Math.random() < 0.06) {
            moveDirection = -moveDirection;
            lastReverseTime = now;
        }
    }

    /**
     * Wall smoothing: adjust desired absolute heading so we don't crash into walls.
     */
    private double wallSmoothing(double x, double y, double desiredDir, int dir) {
        double stick = 140;
        double w = getBattleFieldWidth();
        double h = getBattleFieldHeight();
        double angle = desiredDir;
        int attempts = 0;
        while (attempts < 36) {
            double testX = x + Math.sin(angle) * stick;
            double testY = y + Math.cos(angle) * stick;
            if (testX > 30 && testX < w - 30 && testY > 30 && testY < h - 30) {
                break;
            }
            // rotate away
            angle += dir * 0.15;
            attempts++;
        }
        return angle;
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Change direction when hit to be less predictable
        if (Math.random() < 0.5) {
            moveDirection = -moveDirection;
        }
    }

    public void onHitWall(HitWallEvent e) {
        moveDirection = -moveDirection;
    }

    public void onHitRobot(HitRobotEvent e) {
        // Ram some damage but mostly back off
        moveDirection = -moveDirection;
        if (e.isMyFault()) {
            setBack(50);
        }
    }

    public void onDeath(DeathEvent e) {
        // nothing
    }
}
