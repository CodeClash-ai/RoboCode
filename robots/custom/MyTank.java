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
        // ROUND-1 vs tibola__markiv: hit rate ~40-50% up to ~500px -> every power tier
        // is net-energy-positive and power 3.0 maximizes dmg/shot. Widened the 3.0
        // tier to cover our new ~450px orbit distance.
        // ROUND-2 vs tibola__markiv: we win 97% but LOSE 8 long energy-war grinds.
        // Real hit rate by distance (replay-sim, 120 games, W=0.75):
        //   <300px 52%, 300-450px 29%, 450-550px 31%, >550px 18.5%.
        // Net energy/shot = hitrate*3*power - power (positive iff hitrate>1/3).
        // At our ~450-550px engagement (~30% hit) every power is slightly NET-
        // NEGATIVE, so power-3 misses drain us faster than this energy-conserving
        // enemy (it fires only ~14 shots/game vs our ~30). To WIN the grinds:
        //  - keep power 3.0 only where hit rate clears break-even (<300px, 52%),
        //  - taper power at mid/long range so each miss costs LESS energy while
        //    still landing meaningful damage on hits.
        // ==== TUNING vs robo_code__regullarmonk (LINEAR OSCILLATOR) ====
        // This opponent moves back-and-forth along a FIXED heading (never turns),
        // pausing at each endpoint (~54% of ticks stationary). Replay-sim over 250
        // recorded games shows HEAD-ON aim (W=1.0) hits ~13% overall but is DOUBLE
        // the full-lead rate (6.8%) -- linear lead overshoots its pauses/reversals.
        // Hit rate is dominated by DISTANCE: 100-200px=66%, 200-300px=29%,
        // 400-500px=9%. Prior versions orbited at ~485px (only 3% of ticks <300px)
        // and LOST the energy war (our finalE 7 vs enemy 26). Fix: orbit CLOSER
        // (~330px) to raise hit rate, and use head-on aim.
        // ROUND-2 vs regullarmonk power tiers by measured hit rate (net = hr*3p-p,
        // break-even at hr>1/3): <200px hr~66% & <300px hr~36% are net-POSITIVE at
        // full power 3.0. The 300-400px zone is only 17% hit (net -1.48 at p3.0),
        // so taper power there so each miss costs LESS while we pull back inside.
        // ==== TUNING vs andrekorol__oppswantmedead (SLOW STRAIGHT-LINE MOVER) ====
        // Round 0: we won 100% (250/250), 92% share, worst final E 25 (enemy dies
        // every game). Opponent = slow straight mover (avg |v| 2.1, moving 46% of
        // ticks, avg |dh| = 0.0 -> NEVER turns body). Head-on (W=1.0) is monotonically
        // best (W-sweep: 34.5%@0.0 -> 50.0%@1.0). Head-on hit rate BY DISTANCE (p3.0):
        //   0-200px 66-98%, 200-300 48%, 300-400 52%, 400-500 46%, 500-600 37%.
        // Net energy/shot = hr*3p - p; break-even at hr>1/3. EVERY bucket clears it
        // even at 500-600px (37% = +0.36 net @ p3). So the old regullarmonk taper
        // (1.6/1.8 at 300-500px) was leaving damage on the table -- those buckets are
        // 46-52% here, not 17%. This opponent loses the energy war decisively, so
        // use FULL power out to 550px for more bullet damage -> higher share + faster
        // kills. Beyond 550px hit rate drops (37%) so keep a modest taper there.
        if (dist < 550)       power = 3.0;   // 37-98% hit here -> full power, net-positive
        else if (dist < 650)  power = 2.0;   // ~37-58% hit
        else                  power = 1.5;   // long range -> smaller drain if a miss

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
        double W = 1.0;  // HEAD-ON: best vs regullarmonk linear oscillator (replay-sim 13% vs 6.8% lead)
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
        // ROUND-2 vs tibola__markiv: skip low-confidence long-range shots that
        // bleed us in the grind. Only fire far when we hold an energy lead, and
        // require tighter gun alignment at range (aim error hurts more far away).
        // ROUND-2 vs regullarmonk: conserve energy in the low-hit-rate zones.
        // Only fire at 300px+ (hit rate <=23%) when we still hold an energy lead,
        // so a bad long-range streak can't drain us below the enemy in the grind.
        // Up close (<300px, 36-66% hit) always fire -- those shots gain energy.
        // vs oppswantmedead: every distance bucket is net-energy-positive (37%+
        // hit even at 500-600px) and we win the energy war decisively, so only gate
        // the truly long, low-hit shots (>550px) when we're behind on energy.
        boolean allowFire = true;
        if (dist > 550 && getEnergy() < enemyEnergy) allowFire = false;
        // Tighter alignment for distant shots (bullet spread grows with range).
        double alignThresh = (dist > 400) ? 0.09 : 0.12;

        if (allowFire && getGunHeat() == 0 && Math.abs(gunTurn) < alignThresh
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
        // ROUND-1 vs tibola__markiv: enemy's gun hits us MOST at 200-400px (2900+
        // hits) and drops off sharply beyond 450px (139 hits >500). It out-trades us
        // in 13 losses at close range. Orbit FURTHER OUT (~450px) to slash enemy
        // accuracy; our own hit rate stays ~40% at 450-550px per replay-sim.
        // vs regullarmonk (linear oscillator): orbit CLOSER (~330px) to boost our
        // head-on hit rate (66% at 100-200px, 29% at 200-300px, only 9% at 450px).
        // ROUND-2 vs regullarmonk: analysis of 250 recorded games shows the
        // 300-400px zone is a LOSE-LOSE (our hit rate only 17%, enemy hit
        // density HIGHEST at 6.8/1k ticks) yet we spent 41k/68k ticks there
        // orbiting ~330px and LOST the energy war in 55/250 games. At 200-300px
        // our hit rate DOUBLES to 36% (net-energy-POSITIVE) while enemy density
        // DROPS to 5.4/1k; at 100-200px our hit is 66% with only 4.5/1k enemy.
        // -> Orbit MUCH CLOSER (~230px) to move BOTH our accuracy up and out of
        // the enemy's kill zone. This flips the energy war in our favor.
        double rangeBias = 0.0;
        if (enemyDistance > 270) rangeBias = -0.55;      // pull in toward ~230px
        else if (enemyDistance < 180) rangeBias = 0.55;  // push out if too close

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
        // ROUND-1 vs tibola__markiv: this opponent out-trades us in the losses by
        // landing many hits at close range. Now that we orbit further out, its
        // bullets take longer to arrive so reactive dodging is more effective.
        // Reverse ~60% on detected enemy fire (still randomized, not a strict
        // alternation), plus rare random reversals to break any residual period.
        long now = getTime();
        if (enemyFired && now - lastReverseTime >= 5 && Math.random() < 0.6) {
            moveDirection = -moveDirection;
            lastReverseTime = now;
        } else if (now - lastReverseTime >= 8 && Math.random() < 0.07) {
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
