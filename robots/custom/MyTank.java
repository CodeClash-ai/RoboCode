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
        // ==== ENERGY-WAR TUNING vs barriosnahuel__tirolio ====
        // This opponent is a full-speed dodger that CONSERVES energy (fires ~0.4
        // shots/game). It wins by SURVIVAL: it lets us drain ourselves with missed
        // power-3 shots (~13% hit) while it takes almost no damage. Replay sim over
        // 250 recorded games shows:
        //   * BEST aim = W=0.5 (50% current + 50% linear lead): 26.7% hit overall
        //     vs only 9.9% for pure head-on (W=1.0). It moves at constant velocity
        //     so a real lead is needed, but its reactive reversals mean a full lead
        //     over-shoots -> half-lead is optimal.
        //   * Hit rate by distance: near(<250)=61%, mid(250-450)=37%, far(>450)=19%.
        //   * Net energy per shot (fire cost vs 3*power gained on hit):
        //       power1.0 -> +0.11/shot, power1.5 -> +0.05, power3.0 -> -0.60.
        //     => LOW power far away GAINS energy; HIGH power only pays off up close
        //     where hit rate is high. So: power scales with (short) distance.
        double dist = Point2D.distance(getX(), getY(), enemyX, enemyY);

        double power;
        // ==== TUNING vs robo_code__crazy (wall-bouncing, wide-arc curving mover) ====
        // Replay-sim over 100 recorded games (per-tick interception on the enemy's
        // actual future path) shows this opponent curves so hard and bounces off
        // walls so often that ANY lead (linear or circular) overshoots. HEAD-ON aim
        // (W=1.0, aim at current position) is by far the best: 27.8% hit / 4.45
        // dmg/shot, vs full-linear-lead (W=0.0) only 13.3% / 2.12, circular 18.3%.
        // Flat power 3.0 maximizes dmg/shot (tiering lowered it). We win 100% with
        // ~60 avg min energy, so max power = max bullet damage = more score share.
        // ==== ROUND-2 FIX vs robo_code__crazy ====
        // Round 0 (distance-tiered power, W=0.0) won 100% (249/250).
        // Round 1 (flat power 3.0, W=1.0) REGRESSED to 83% (207/250, 43 losses):
        // firing power-3 constantly at a fast wall-bouncing dodger with ~16% real
        // hit rate DRAINS us in the energy war (losses had enemy still at 40-50 E
        // while we died at 0). Distance-tiered power keeps far shots net-positive.
        // ROUND-4 (vs it_economics__ite_ctbot): replay-sim shows EVERY power tier is
        // net-energy-POSITIVE against this opponent (power3.0 = +2.59 E/shot,
        // 34.9% hit even including far shots). Unlike prior energy-conserving
        // dodgers, this bot loses the energy war to us, so max power = fastest
        // kills + most damage share. Keep a mild far-range taper for hit-rate
        // efficiency but stay high.
        if (dist < 350)       power = 3.0;
        else if (dist < 550)  power = 2.5;
        else                  power = 2.0;

        // Energy safety clamps so a bad streak can't self-destruct us.
        if (getEnergy() < 30) power = Math.min(power, 2.0);
        if (getEnergy() < 15) power = Math.min(power, 1.0);
        if (getEnergy() < 6)  power = Math.min(power, 0.4);
        power = Math.max(0.1, Math.min(power, 3.0));

        double bulletSpeed = 20 - 3 * power;

        // Iterative linear lead prediction over bullet flight time.
        // Replay-sim over pez__droidpoet paths shows this near-constant-velocity
        // full-speed mover is best hit with a FULL lead (W=0.0): W=0.0 gave 20.9%
        // vs 17.6% for the old half-lead, and ~90 vs ~75 avg bullet dmg/round.
        double leadX = enemyX, leadY = enemyY;
        for (int it = 0; it < 12; it++) {
            double fd = Math.hypot(leadX - getX(), leadY - getY());
            double ft = fd / bulletSpeed;
            leadX = enemyX + Math.sin(enemyHeading) * enemyVelocity * ft;
            leadY = enemyY + Math.cos(enemyHeading) * enemyVelocity * ft;
        }
        // ROUND-2 FIX: revert to W=0.0 (full linear lead) which WON 100% (round 0).
        // The round-1 head-on (W=1.0) change coincided with the regression to 83%.
        // ROUND-4 (vs it_economics__ite_ctbot): replay-sim over 60 games shows
        // W=0.5 (half-lead) hits 41.3% vs W=0.0 33.8%. This opponent is a slow,
        // lightly-curving mover (28% stationary, avg 0.23 deg/tick turn, rarely
        // full speed) so a full linear lead overshoots; half-lead is optimal.
        // ROUND-1 (vs it_economics__ite_simple): near-constant-velocity mover
        // (moving 85% of ticks, avg |v| 4.24, turn only 0.013 rad/tick). Replay-sim
        // over two independent 80-game slices: W=0.25 hits 24-31% vs W=0.5 21-24%
        // and W=0.0 17-20% -- clean peak at 0.25. Slightly-less-than-half lead is
        // optimal for this fast straight mover (full lead overshoots its rare curves).
        // ROUND-1 (vs it_economics__ite_terminator): SLOW mover (avg |v| 2.56,
        // moving ~51% of ticks, turn 0.026 rad/tick). Replay-sim over TWO independent
        // 80-game slices shows a clean MONOTONIC rise toward head-on:
        //   W=0.0 ~33%, W=0.25 ~34%, W=0.5 ~37%, W=0.75 ~42%, W=1.0 ~50% hit.
        // A slow, lightly-curving target is best hit near head-on (any lead overshoots).
        // We win the energy war (finalE ~107, worst 58) so no drain risk; power tiers
        // (distance-based, kept net-positive) still guard the crazy-bot regression.
        // Chose W=0.85: strongly toward head-on (physics: slow target -> head-on best),
        // hedged just short of pure 1.0 since replay is biased by the reactive enemy path.
        double W = 0.85;
        double predX = W * enemyX + (1 - W) * leadX;
        double predY = W * enemyY + (1 - W) * leadY;

        // Clamp prediction inside the field.
        double bw = getBattleFieldWidth(), bh = getBattleFieldHeight();
        predX = Math.max(18, Math.min(bw - 18, predX));
        predY = Math.max(18, Math.min(bh - 18, predY));

        double aimAngle = Math.atan2(predX - getX(), predY - getY());
        double gunTurn = Utils.normalRelativeAngle(aimAngle - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        // Don't waste far-range shots when energy is tight: only fire far shots if
        // we still hold an energy lead. Up close always fire (high hit rate).
        boolean allowFire = true;
        if (dist > 550 && getEnergy() < enemyEnergy + 5) allowFire = false;

        if (allowFire && getGunHeat() == 0 && Math.abs(gunTurn) < 0.12
                && getEnergy() > power + 0.5) {
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
        if (enemyDistance > 350) rangeBias = -0.42;      // pull in (target ~280px for higher hit rate)
        else if (enemyDistance < 200) rangeBias = 0.55;  // push out (avoid ramming)

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
