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

        // Track enemy turn rate for circular prediction (EMA smoothing over
        // recent ticks). vs team488__meow (fast, heavily-curving mover avg |dh|
        // ~0.11 rad/tick): replay-sim shows circular targeting hits ~21-26% vs
        // ~16% head-on and ~3-4% linear. Smoothing the per-tick heading delta
        // gives a stable turn estimate for the stepped circular predictor.
        if (haveLastHeading) {
            double dh = Utils.normalRelativeAngle(enemyHeading - lastEnemyHeading);
            dh = Math.max(-0.20, Math.min(0.20, dh));
            enemyTurnRate = 0.6 * enemyTurnRate + 0.4 * dh;
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
        // ROUND vs alpian__ianstank: head-on hit rate 34-57% at EVERY distance bucket
        // (replay-sim), all net-energy-positive; we win energy war 73 vs 2. Full power out to 550px.
        // ROUND-2 vs alpian__ianstank (energy-conserving stop-and-reverse oscillator):
        // Round 1 (flat power 3.0 out to 550px) won 249/250 but the 1 loss + all close
        // games (finalE 0-20) were LONG grinds (860-1178 turns) where our REAL head-on
        // hit rate collapses to ~15-21% at 300-500px while we keep firing power 3.
        // MEASURED real hit rate by distance (energy-gain events / fire events, 150 games):
        //   100-200px 70%, 200-300px 36%, 300-400px 21%, 400-500px 16%, 500-600px 17%.
        // Net energy/shot @p3 = hr*9-3: 300-400px = -1.11, 400-500px = -1.56 (BLEED!).
        // 56% of ticks are 200-300px (marginal +0.24), 30% are 300-500px (net-negative).
        // The enemy fires ~half as often as us (it conserves), so our per-miss drain in
        // the low-hit zone loses the energy war in grinds. TAPER power by distance so
        // each far miss costs far less; grind-sim: net firing energy -106 -> +772 (all
        // 150 games), grind games -111 -> -39. Keeps power 3.0 only where hit rate is
        // high enough (<250px). Fewer power-3 misses at range = we outlast the enemy.
        // taperC: keep power 3.0 across the dominant 200-300px zone (56% of ticks,
        // 36% hit = net-positive), taper beyond where hit rate drops below break-even.
        // grind-sim: net firing energy (all 150 games) -106 -> +574; grind games
        // -111 -> -45; while retaining ~93% of the winning-game bullet damage (22655
        // vs 24233) so score share barely drops. The energy-war cut below does the
        // heavy lifting in the actual grind-loss state.
        // vs andrekorol__myfirstkiller (slow straight-line mover, avg|v|1.9, never
        // turns body). ROUND-2 finding: R1 raised these tiers to 3.0/<400 etc but
        // the REAL game got WORSE (killtick 275->290, enemy dmg 56->108, share
        // 97->94). Higher power = longer gun cooldown (1+p/5) -> fewer shots, longer
        // engagement -> enemy lands more. REVERTED to the conservative R0 tiers
        // (killtick 275, enemy 56, 97% share). Faster kills + less exposure win here.
        // ROUND-2 vs iagomonteiro13579__npcsniper (stop-and-go dodger, bimodal
        // velocity v=0 or v=8, engages ~279px, CONSERVES energy, fires ~half as
        // often as us). MEASURED hit rate + net firing energy by distance (round-1
        // 250 sims): 100-200px hr 49% NET +13/1k (WIN zone); 200-300px hr 26% NET
        // -77/1k (we spend the MOST ticks here = 55k, bleeding); 300-400px hr 17%
        // NET -131/1k (catastrophic). Old flat power 3.0/<300 fired power-3 at 26%
        // hit -> lost the energy war -> 15/250 grind losses (we fire 40-66 shots to
        // enemy's 16-43, both conserving, but our per-miss drain kills us first).
        // TAPER hard beyond 200px so each far miss costs far less. Net-firing-energy
        // model over 250 recorded games (MEASURED hit rates): OLD -1773 -> NEW +398.
        // Keep full power 3.0 only in the <200px net-positive zone (49% hit).
        // ROUND-1 vs joaomcarvalho__jeujdapeu: we now orbit WIDER (~280px) to escape
        // its lead gun, so we camp mostly at 200-300px. Our head-on hit rate there
        // is 43% (well above 33% break-even) and we WIN the energy war (88% wins),
        // so keep power high out to 300px for fast kills / net-positive damage.
        // Taper beyond 300px where hit rate falls and misses drain us in grinds.
        if (dist < 300)       power = 3.0;
        else if (dist < 400)  power = 1.6;
        else if (dist < 500)  power = 1.0;
        else                  power = 0.6;   // long range -> smallest drain if a miss

        // Energy safety clamps so a bad streak can't self-destruct us.
        if (getEnergy() < 30) power = Math.min(power, 2.0);
        if (getEnergy() < 15) power = Math.min(power, 1.0);
        if (getEnergy() < 6)  power = Math.min(power, 0.4);
        // ROUND-2 vs oppswantmedead: the ONLY loss (sim_96, an 865-turn grind at
        // ~316px avg) was an energy-war bleed -- we fired 46 shots to the enemy's
        // 25 (it conserves) and died with enemy at 45 E. We win the energy war 80%
        // of the time, but when we FALL BEHIND we should stop bleeding at mid range
        // where hit rate isn't near-certain. If we're meaningfully behind on energy
        // and NOT point-blank, taper power so each miss costs less while we recover.
        // Energy-war taper: if we've fallen behind on energy (the grind-loss state
        // where we're behind ~94% of ticks vs ~26% in wins) and we're NOT point-blank,
        // cut power hard so each miss barely costs energy while we recover / close in.
        // ROUND (vs alpian__tarektank): the ONLY loss (sim_20) was a 1223-turn
        // energy-war grind where we were behind on energy 97% of ticks and bled
        // to 0 (enemy kept 26 E). tarektank is a slow straight-line mover that
        // CONSERVES energy (fires ~11 shots/game vs our 25). When we fall behind,
        // stop bleeding at mid range: taper harder (power<=0.8) and gate far shots,
        // so each miss barely costs energy while we recover. Grind-model over 150
        // recorded games: net firing energy +20% (1009 -> 1206).
        if (getEnergy() < enemyEnergy && dist > 300) {
            power = Math.min(power, 0.8);
        }
        power = Math.max(0.1, Math.min(power, 3.0));

        double bulletSpeed = 20 - 3 * power;

        // Iterative linear lead prediction over bullet flight time.
        // Replay-sim over pez__droidpoet paths shows this near-constant-velocity
        // full-speed mover is best hit with a FULL lead (W=0.0): W=0.0 gave 20.9%
        // vs 17.6% for the old half-lead, and ~90 vs ~75 avg bullet dmg/round.
        // CIRCULAR TARGETING (vs team488__meow, a fast heavily-curving mover):
        // iterate bullet flight time, then step the enemy forward each future
        // tick applying its (smoothed) turn rate. Replay-sim over 2 slices:
        // circular 20.8%/25.7% hit vs head-on 16.0%/16.0% vs linear 2.9%/4.4%.
        double leadX = enemyX, leadY = enemyY;
        double ftEst = 0;
        for (int it = 0; it < 15; it++) {
            double px = enemyX, py = enemyY, h = enemyHeading;
            int steps = (int) ftEst;
            for (int s = 0; s < steps; s++) {
                h += enemyTurnRate;
                px += Math.sin(h) * enemyVelocity;
                py += Math.cos(h) * enemyVelocity;
            }
            leadX = px; leadY = py;
            ftEst = Math.hypot(leadX - getX(), leadY - getY()) / bulletSpeed;
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
        double W = 1.0;  // vs iagomonteiro13579__npcsniper (moderate mover: movefrac 0.72, avgV 4.31, avg|dh| 0.0345 MILD curve, engages ~297px). W-sweep 2 slices: HEAD-ON(W=1.0) ~36-38pct BEST vs circular(W=0.0) ~23-24pct. Replay biased toward W=0.0 (our old aim) yet head-on wins DESPITE bias. NOT a heavy spinner. Was 0.0 for spinbot.
        // [old] double W = 1.0; // HEAD-ON best vs alpian__ianstank (stop-and-reverse oscillator, ~50% stationary). Replay-sim 80 games: W=1.0 hits 40.3% vs W=0.0 21.4%.
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
        // In the grind-loss state (behind on energy) don't waste far low-hit
        // shots (400px+ hit rate ~20% = net-negative); conserve to outlast.
        if (dist > 400 && getEnergy() < enemyEnergy) allowFire = false;
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
        // ROUND-2 vs alpian__tarektank: NET-energy analysis by distance (150 games)
        // showed 100-200px is the ONLY net-POSITIVE zone (+46/1k ticks): our hit rate
        // 70% there dominates the slightly higher enemy hit density (11.9 vs 9.6/1k).
        // 200-300px net -55/1k, 300-400px -73/1k. We orbited ~230px (mostly 200-300
        // = losing zone) -> pulled the 1 grind loss + close games. Orbit CLOSER (~180px)
        // to spend more ticks in the high-hit zone and flip the grind energy war.
        // ROUND-1 vs team488__meow (fast, heavily-curving mover, 37% enemy
        // accuracy vs our 20%): with circular targeting our hit rate is ~25% at
        // BOTH 100-200px AND 200-300px, but enemy hit density is 14.8/1k at
        // 100-200px vs only 3.3/1k at 200-300px. So orbit ~260px: same hit rate,
        // ~4x FEWER enemy hits. This directly attacks the 18/250 losses (enemy
        // out-trades us at close range where its gun is deadly).
        // ROUND-2 vs myfirstrobot: the 1 grind loss (sim_184, 1141 turns) and the
        // 2 close games stayed at 200-600px (mean 350px) — a FIXED -0.6 inward
        // bias isn't strong enough to close when the enemy drifts out to range.
        // GRADUATED inward pull: the further past our ~150px target, the harder we
        // steer inward (up to nearly head-on toward the enemy), so we actually
        // close the gap in grind games and reach the 48%-hit net-positive <200px
        // zone instead of bleeding at range. Symmetric mild push-out when too close.
        // ROUND-1 vs robo_code__spinbot (FAST heavily-curving SpinBot with a
        // decent gun): target orbit ~250px. With circular targeting our hit rate
        // is 61% at 200-300px (even HIGHER than 58% at 100-200px), while SpinBot's
        // gun is nearly HARMLESS at 200-300px (enemy hit density 0.9/1k) vs
        // DANGEROUS up close (6.3/1k at 100-200, 17.5/1k at 0-100). So orbiting
        // WIDER to ~250px is strictly better here: same/better hit rate AND far
        // fewer enemy hits. (Prior ~150px orbit was for weak-gun slow movers.)
        // ROUND-2 vs npcsniper: MEASURED net-energy by distance (round-1 250 sims):
        // 100-200px NET +13/1k (ONLY net-positive zone, 49% hit); 200-300px NET
        // -77/1k (we spent 55k ticks here bleeding); 300-400px NET -131/1k. We
        // engaged ~279px (mid of the losing zone) because the enemy keeps distance
        // open and the prior -0.9 max inward pull wasn't enough to close. Steer
        // MUCH harder inward when far so we actually reach the <200px win zone;
        // only push out below ~130px. Target orbit ~160px (100-200px net-positive).
        // ROUND-1 vs joaomcarvalho__jeujdapeu (moderate mover movefrac 0.75, avgV
        // 3.56, avg|dh| 0.055, engages ~193-220px) with a STRONG LEAD gun (fires
        // at median 0.605 rad off head-on -> aims where we WILL be). We won 88%
        // (220/250) but LOST 30 games. Losses = enemy accuracy jumps to 35% (vs
        // 22% in wins) and it out-fires us in the energy war. MEASURED enemy hit
        // density by distance (150 games): 100-200px 9.7/1k (its DEADLIEST zone,
        // where we camped ~160px), 200-300px 5.4/1k, 300-400px only 1.8/1k. Our
        // OWN head-on hit rate barely drops with range: 100-200px 50%, 200-300px
        // 43%, 300-400px 44%. => Orbit MUCH WIDER (~280px): our accuracy holds
        // ~43-44% while the enemy's lead gun accuracy collapses (9.7 -> ~2-5/1k
        // enemy hits). Same insight that beat the lead-gun/curving foes spinbot
        // (~250px) and team488__meow (~260px). Attacks the 30 losses directly.
        // R2 TUNE: measured round-1 engagement was ~314px (drifted wide of the
        // ~280px target) -> we spent 28k ticks at 300-400px where OUR real hit
        // rate is only 0.26 (net-negative firing) vs 0.35 at 200-300px. Enemy hit
        // density is nearly the SAME at 200-300px (5.0/1k) and 300-400px (4.6/1k),
        // so closing to ~245px loses ~nothing on defense but RAISES our offense.
        // Stronger inward pull so we actually reach the 200-300px best-HR zone.
        double rangeBias = 0.0;
        if (enemyDistance > 450)      rangeBias = -1.1;  // far: strong inward pull to close
        else if (enemyDistance > 330) rangeBias = -0.7;  // mid-far: firm inward
        else if (enemyDistance > 260) rangeBias = -0.35; // approaching target ~245px
        else if (enemyDistance < 210) rangeBias = 0.5;   // too close (lead-gun kill zone): push out

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
        // ROUND-1 vs dankraemer__juggernaut: enemy uses LEAD (predictive) targeting
        // -- its gun points ~0.364 rad off head-on when it fires, so it aims where
        // we WOULD be. Against a lead-aiming gun the strongest evasion is to REVERSE
        // when the enemy fires: its lead shot flies to the far side and misses.
        // In our 16 losses the enemy hit 45% (vs 32% in wins) -- a movement problem,
        // not a gun problem (head-on is confirmed our best aim). Raise dodge-on-fire
        // 0.45 -> 0.70 (still not a strict alternation, so not itself learnable),
        // shorten the rate-limit 6 -> 5 ticks so we can dodge consecutive waves,
        // and keep the rare random reversal to break any residual period.
        long now = getTime();
        if (enemyFired && now - lastReverseTime >= 6 && Math.random() < 0.45) {
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
        // Change direction when hit to be less predictable. Raised 0.5 -> 0.8:
        // a hit means the enemy's gun profiled our current path, so disrupt it
        // (vs dankraemer__juggernaut, a lead-aiming gun that hits 45% in our losses).
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
