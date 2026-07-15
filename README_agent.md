# Agent Notes (Round 1)

## Current bot: robots/custom/MyTank.java
Rewrote the weak "TearsofSteel/Seesaw" starter into a competitive 1v1 AdvancedRobot:
- Radar: infinite-lock narrow sweep on the single enemy.
- Gun: iterative circular/linear predictive targeting; bullet power scaled by
  distance and remaining energy; only fires when gun is aligned & cool.
- Movement: orbital (perpendicular) motion around enemy with wall smoothing,
  random reversals, and dodge-on-enemy-fire (energy-drop detection).
- Reacts to onHitByBullet / onHitWall / onHitRobot with direction changes.

## CRITICAL BUILD NOTE
The default `javac` here is Java 24 -> class file major version 68, which
Robocode 1.10 CANNOT load ("Can't find 'custom.MyTank'").
ALWAYS compile targeting Java 8 bytecode:

    javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java

The starter MyTank.class was committed as Java 24 which would have failed to load.
Committed .class is now major version 52 (Java 8). Verify with:
    javap -v robots/custom/MyTank.class | grep "major version"   # want 52

## Local test harness (flaky)
`./robocode.sh` doesn't pass the required --add-opens flags for Java 24 and the
robot.database rebuild races with battle start, so local battles frequently print
"Can't find" and empty results. To attempt a local run:
    java -cp "libs/*" -DROBOTPATH=/workspace/robots \
      --add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED \
      --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
      --add-opens=java.desktop/javax.swing.text=ALL-UNNAMED \
      --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
      robocode.Robocode -battle <battle> -nodisplay -nosound -results /tmp/res.txt
Sample robots (sample.Corners etc.) are NOT present in this install, so build a
test opponent from git history if you want head-to-head numbers, e.g.:
    git show 810cb53:robots/custom/MyTank.java | sed 's/package custom;/package testbot;/;...'

## Opponent
Round 0 log was a solo sim (only opus_4_8 present); opponent identity unknown.
Assume a generic decent bot. Predictive gun + orbital movement should beat
simple movers/fixed-gun bots.

## Ideas for next teammate
- Add multiple gun modes (head-on / linear / circular) and pick best by hit stats.
- Wave surfing for movement (bigger win, more code).
- Tune bullet power vs. distance further.

# Agent Notes (Round 2)

## KEY FINDING: opponent never spawned
In rounds 0 and 1 the sim logs (`/logs/rounds/*/sim_*.jsonl`) show ONLY `opus_4_8`
in the battle — the opponent `technischeinformatica__tearsofsteel` did not load.
My bot won 100% of games by default (survival), but trace.md shows it did
NOTHING (0 shots, 0 speed) because there was no enemy to scan. This is expected
behavior with no opponent: radar spins, bot survives, bot wins.

## Compilation
- git tracks only MyTank.java (NOT .class). The real game harness compiles the
  source itself (sim logs prove it ran). Locally, ALWAYS build Java-8 bytecode:
    javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java
  Verify: javap -v robots/custom/MyTank.class | grep "major version"  # want 52
- Local ./robocode battles are BROKEN here ("Can't find custom.MyTank" every run,
  even with a fresh robot.database and Java-8 classes). Do NOT trust local battle
  results; rely on the real harness sim logs in /logs/.

## Changes this round
- Added true circular-targeting: track enemy per-tick turn rate
  (enemyTurnRate, clamped +-0.15 rad) and apply it in the gun predictor loop.
  Helps hit curving/orbiting opponents if a real enemy ever appears.
- Bot is otherwise unchanged (orbital movement, wall smoothing, dodge-on-fire).

## If opponent stays absent
Nothing to do — we win by survival. Just keep MyTank.java compiling to Java 8.

## Ideas for next teammate
- If a real opponent shows up in future sim logs, analyze its movement pattern
  from /logs/rounds/N/sim_*.jsonl (enemy x,y,heading,velocity) and tune the gun.
- Consider wave surfing for stronger evasion.

# Agent Notes (Round 3 / this round)

## KEY FINDING: opponent (wouterjoosse__infinitylock) is STATIONARY
Analyzed /logs/rounds/0/sim_*.jsonl: enemy velocity is 0.0 for the ENTIRE match
across all sims. It never drives. Round 0 result: we won 100% (10/10 rounds,
score 1800 vs 0), finishing each round with ~136 energy (we take ~zero damage).

## Change this round
- aimAndFire(): when |enemyVelocity| < 1.0, force bullet power = 3.0. Against a
  stationary target we ALWAYS hit, so max power = faster kills + bigger damage
  margin, with no downside. Moving-enemy logic (circular prediction, distance-
  scaled power) is untouched as a fallback if a mobile enemy ever appears.
- Verified compiles to Java 8 (major version 52). rc=0.

## Analysis one-liner (enemy movement check)
python3 -c "import json,glob;
[print(fn, any(abs(u['v'])>0.1 for l in open(fn) if 'u' in (d:=json.loads(l)) for u in d['u'] if u['i']==0)) for fn in sorted(glob.glob('/logs/rounds/0/sim_*.jsonl'))[:5]]"

## Recommendation for next teammate
We dominate. Keep MyTank.java compiling to Java 8. Only risk is a compile break
or the opponent suddenly becoming mobile — current bot handles both. Low priority
to change further; focus on verifying the win margin stays 100% in new logs.

# Agent Notes (Round 2 replay / verification pass)

## STATUS: PERFECT WIN — DO NOT RISK REGRESSION
Verified /logs/rounds/1/results_0.txt: opus_4_8.MyTank 1800 (100%) vs
wouterjoosse__infinitylock.MyTank 0 (0%). 10/10 first places.
results.json: winner opus-4-8, score 45000 vs 0.

## Opponent confirmed STATIONARY (round 1 sim logs)
Both robots now spawn (unlike rounds 0/1 in older notes). Checked
/logs/rounds/1/sim_{0,50,100,200}.jsonl: enemy (i=1) velocity is 0.0 for ALL
ticks. Enemy never moves and never damages us. We kill it every game
(enemy final energy = 0.0). In sim_0 we kill at tick ~162 of 312, ending with
133 energy (we gain energy from bullet hits; enemy fires ~10 low-power shots
early but they miss/we out-trade).

## Decision this pass: NO code change
Bot already achieves the maximum possible score (survival + all bonuses).
Faster kills would NOT increase score. Any gameplay edit only adds regression
risk. Left MyTank.java unchanged. Reconfirmed it compiles to Java 8:
    javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java
    javap -v robots/custom/MyTank.class | grep "major version"  # -> 52  (OK)

## For next teammate
- ONLY act if a NEW /logs/rounds/N log shows the enemy moving OR our win margin
  dropping. Analysis one-liner (per-sim enemy movement + final energy):
    cd /logs/rounds/<N> && python3 -c "
import json
for fn in ['sim_0.jsonl','sim_50.jsonl','sim_100.jsonl']:
    ls=[json.loads(l) for l in open(fn) if l.strip()]
    mv=0;tmax=0;fe=100
    for d in ls:
        if 't' in d: tmax=d['t']
        if 'u' in d:
            for u in d['u']:
                if u['i']==1:
                    if abs(u['v'])>0.1: mv+=1
                    fe=u['e']
    print(fn,tmax,mv,fe)"
- If enemy becomes mobile: the predictive gun (circular, enemyTurnRate) and
  orbital+dodge movement already handle it; just tune bullet power/thresholds.
- Keep MyTank class name and Java-8 bytecode. That's the only hard requirement.

# Agent Notes (Round 1 / current pass) — opponent = robo_code__sittingduck

## STATUS: PERFECT WIN — NO CODE CHANGE MADE
Opponent this round is `robo_code__sittingduck` (literally a stationary sitting
duck). Verified from /logs/rounds/0:
- results.json: opus-4-8 45000 vs robo_code__sittingduck 0.
- results_0.txt: opus_4_8.MyTank 1800 (100%), 10/10 first places.
- sim logs: sittingduck (index i=0 — NOTE index mapping is per-file in the header
  {"robots":{"0":"...","1":"..."}}) has 0 moves and dies (finalE 0.0). We (i=1)
  finish each round with ~130-136 energy.

## Index-mapping gotcha (important for analysis)
The sim_*.jsonl header maps robot index -> name and it is NOT fixed. In round 0
here, i=0 = sittingduck and i=1 = opus_4_8 (reversed vs older notes). ALWAYS read
the first line's "robots" dict before interpreting u[].i in the analysis scripts.

## Decision
Bot already scores the theoretical maximum. The stationary-target branch
(aimAndFire: |enemyVelocity|<1.0 -> power=3.0) is already present and correct.
Left MyTank.java untouched. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK, major version 52

## For next teammate
Only act if a NEW /logs log shows the enemy moving or the win margin dropping.
Keep MyTank class name + Java-8 bytecode (the only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = robo_code__sittingduck

## STATUS: PERFECT WIN, MAX SCORE — NO CODE CHANGE
Checked /logs/rounds/{0,1}: opus-4-8 45000/45001 vs robo_code__sittingduck 0.
results_0.txt both rounds: opus_4_8.MyTank 1800 (100%), 10/10 first places.
Opponent is a stationary sitting duck (never moves, never damages us).

## Decision: NO gameplay change
We already score the theoretical maximum (survival + all bonuses maxed). Faster
kills would NOT raise the score. Any edit only adds regression risk. Left
MyTank.java unchanged. The stationary-target branch (aimAndFire: |enemyVel|<1.0
-> power=3.0) is present and correct.

## Verified compile
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs log shows the enemy moving or the win margin dropping.
Keep MyTank class name + Java-8 bytecode (the only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = it_economics__ite_bomax

## KEY FINDING: opponent is a SLOW STOP-AND-GO CRAWLER (mobile but barely)
Unlike previous stationary opponents, it_economics__ite_bomax MOVES. Analyzed
/logs/rounds/0/sim_*.jsonl (i=0 = bomax, i=1 = opus_4_8, per-file header!):
- Pattern: accelerates 0->1->2->3 over ~4 ticks, STOPS for ~4 ticks while
  rotating heading slightly, repeats. Net ~1px/tick average, v peaks at 3.
- It fires low-power bullets (energy 100 start). We still take minimal damage.
- Prior result: opus-4-8 38234 vs bomax 266. 10/10 firsts, 100% win. We finish
  each round with ~116 energy. Mean kill tick 183 (min 96, max 329).

## Changes this round (targeting tuned for stop-and-go)
1. Power boost threshold widened: |enemyVelocity| < 3.5 -> power 3.0 (covers this
   crawler's full 0..3 speed range; we have huge energy margin so max power is
   pure upside for bullet damage).
2. Aim BLEND for slow targets (|v| < 3.5): predX = 0.65*current + 0.35*predicted.
   Rationale: a constant-velocity predictor OVER-shoots a stop-and-go bot (it
   stops ~half its ticks). Heavy weight on current position + slight lead is more
   accurate against this pattern. (Previously we aimed purely predicted.)
Both compile to Java 8 (major version 52), rc=0.

## LOCAL HARNESS STILL BROKEN
Confirmed again: `robocode.Robocode -battle ... -nodisplay` prints
"Can't find 'custom.MyTank'" and empty results even with fresh robot.database
and Java-8 classes. Do NOT trust local battles. Rely on /logs/ sim results.

## For next teammate
- We win 100% with a large margin; changes this round only aim to raise bullet
  damage vs the crawler. If a NEW /logs shows margin dropping or a FASTER/erratic
  mover, revisit the aim blend (raise predicted weight for genuinely fast movers).
- If opponent changes entirely, the general predictive gun + orbital movement
  still handles it. Keep MyTank class name + Java-8 bytecode (hard requirement).
- Analysis one-liner (per-sim enemy movement, kill tick, our final energy):
  cd /logs/rounds/<N> && python3 -c "
import json,glob,statistics
kts=[];ofe=[]
for fn in sorted(glob.glob('sim_*.jsonl')):
    ls=[json.loads(l) for l in open(fn) if l.strip()]
    hdr=ls[0]['robots']
    ei=[int(k) for k,v in hdr.items() if 'opus' not in v][0]; oi=1-ei
    kt=None;fe=100
    for d in ls:
        if 'u' in d:
            for u in d['u']:
                if u['i']==ei and u['e']<=0 and kt is None: kt=d.get('t')
                if u['i']==oi: fe=u['e']
    if kt: kts.append(kt)
    ofe.append(fe)
print('killtick',round(statistics.mean(kts),1),'ourE',round(statistics.mean(ofe),1))"

# Agent Notes (Round 2 / current pass) — opponent = it_economics__ite_bomax

## STATUS: 100% WIN, refining bullet damage
Verified /logs/rounds/{0,1}: opus-4-8 38234/38750 vs bomax 266/304. 10/10 firsts
both rounds (100%/99% score share). We finish ~119 avg energy. Mean kill tick 174.
Enemy = very slow stop-and-go crawler: net ~1px/tick, peaks v=3 for a few ticks
then STOPS for many ticks; heading stays ~0. ~12 of our hits deplete its 100 E.

## Change this round (targeting refinement)
aimAndFire slow-target branch now TIERED:
  - |v| < 1.5  -> aim DIRECTLY at current position (near-stationary = max accuracy)
  - 1.5<=|v|<3.5 -> keep the 0.65 current / 0.35 predicted blend (stop-and-go lead)
Rationale: bomax spends most ticks stopped (v~0), so pure current-position aim is
strictly more accurate there; the blend still covers its brief moving phases.
Compiles Java 8 (major version 52), rc=0. Backup at /tmp/MyTank.bak.java (this pass).

## Local harness still broken (per prior notes) — trust /logs, not local battles.

## For next teammate
- We win 100%; bullet damage (~800/1580 of score) is the only remaining lever.
  If a NEW log shows margin dropping or a faster/erratic mover, raise the predicted
  weight or lower the <1.5 direct-aim threshold. Keep MyTank class name + Java-8.

# Agent Notes (Round 1 / current pass) — opponent = trex22__deepthought

## KEY FINDING: opponent is a STOP-AND-GO DODGER (first real evasive foe)
Analyzed /logs/rounds/0/sim_*.jsonl (header per-file: i=0=trex, i=1=opus).
- Enemy STOPPED (v<0.3) ~54% of ticks; between stops it bursts to v=8 (full
  speed), traveling 100+px, then stops again. Bursts triggered ~when we fire
  (energy-drop dodge). Body heading in scan looked ~0 but it clearly translates.
- Prior result (before my change): opus-4-8 43329 vs trex 343. 10/10 firsts,
  100% win. BUT margin lower than stationary foes: kill tick ~428, we finish
  with only ~83 energy (vs ~130 for sitting ducks). Enemy fires ~power-1.5
  bullets and lands ~26 hits/game on us (~50 E lost).

## Changes this pass (gun + movement)
1. aimAndFire power selection REWRITTEN and TIERED by (stationary vs moving) x range:
   - stationary (|v|<1.0): power 3.0 (<500px) else 2.4 (long range gives it time
     to move before slow bullet lands, so use faster bullet far away).
   - moving dodger: 3.0 (<200px) / 2.2 (<450) / 1.7 (>=450) — faster bullets at
     range arrive before the evasive burst completes; higher power up close where
     bullet flight is short.
2. Aim blend retuned to new thresholds: |v|<1.0 -> aim at current pos (hits the
   54% stopped ticks); 1.0<=|v|<3.0 -> 0.55 cur/0.45 predicted (accel/decel);
   |v|>=3.0 -> full constant-velocity prediction (it holds v=8 mid-burst).
3. Movement now RANGE-CONTROLLED: orbit angle biased inward if dist>550,
   outward if dist<300 (target ~450px sweet spot). Harder to ram, enemy bullets
   take longer to reach us, gun stays accurate.
All compile to Java 8 (major version 52). Backup of prior version: /tmp/MyTank.bak.java
(not persistent across rounds — prior source also in git history).

## LOCAL HARNESS UPDATE
The battle now RUNS 10 rounds locally (java -cp libs/* ... robocode.Robocode
-battle ... -nodisplay) but STILL "Can't find custom.MyTank" -> empty results
(scoring rows blank). So still cannot get local head-to-head numbers. Trust /logs.

## For next teammate
- We win 100%; goal is raising bullet-damage share vs this dodger. If a NEW log
  shows margin dropping, revisit power tiers. Note bullet dmg per hit for power p
  = 4p + 2*max(p-1,0): power3=16, power1.7=7.4. Lower power only wins if it hits
  >~2x more often — I kept powers fairly high to hedge. If enemy dodges our
  stopped-aim shots too (fires-then-moves faster than bullet), lower long-range
  power further.
- Keep MyTank class name + Java-8 bytecode (the only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = trex22__deepthought

## DATA-DRIVEN GUN REWRITE (replay simulation of enemy trajectory)
Built a replay simulator over /logs/rounds/1/sim_*.jsonl that fires our gun from
our recorded position each tick and checks if the bullet intercepts the enemy's
ACTUAL recorded future path. Findings vs this stop-and-go wall-hugging dodger
(stopped ~38% of ticks, bursts to v=8 the rest; body rotates to run along walls):
  - Aim: pure head-on (aim at current pos) = 42.9% hit; pure linear = 31.7%.
    BEST = blend w=0.9 (90% current + 10% linear lead) = 44.2%. Enemy's bursts
    are reactive/random so leading OVER-shoots; heavy current-pos weight wins.
  - Power vs damage/tick (accounts for hit-rate drop + slower cooldown at high
    power): power 3.0 = 0.394 dmg/tick, power 1.9 = 0.30, power 1.0 = 0.157.
    HIGHER POWER WINS because dmg/hit (16 @ p3 vs 9.4 @ p1.9) dominates. Full
    round-sim: p3.0 ~252 potential dmg/round vs p1.7 ~174. Enemy has only 100 E
    so p3.0 also kills FASTER (more bullet-bonus, less time exposed).
  - Hit rate by distance: <400px ~49-51%, 400-600px ~36-43%. So orbit CLOSER.

## Changes made this pass
1. aimAndFire REWRITTEN: power = 3.0 always (energy-scaled down only when low:
   <20->1.5, <10->0.8, <4->0.3). Aim = blend W=0.90 (current 90% / linear 10%).
   Removed the previous distance/velocity power tiers (they measured WORSE:
   round0 killtick 416 -> round1 killtick 460 with the low-power-at-range tiers).
2. Movement range control tightened to target ~400px (pull in >450, push out
   <250) to raise hit rate. Was ~500px avg engagement -> lower accuracy zone.
3. Gun-align fire threshold 0.15 -> 0.12 rad (slightly tighter for accuracy).

## Compile verified
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)
Backup of prior version: /tmp/MyTank.bak.java (NOT persistent; also in git).

## REPLAY SIMULATOR (reusable analysis tool)
The per-tick interception replay is the key tool. To re-run for a new opponent,
load sim_*.jsonl (per-file header maps index->name; enemy = the non-'opus' one),
then for each tick fire a bullet from our (x,y) along an aim strategy and step it
forward at speed 20-3*power checking distance<20 to enemy's recorded future pos.
See the python snippets in this file's git history / the commands used this round.

## For next teammate
- If opponent unchanged: current config is data-optimal for bullet damage. Only
  risk is regression; verify new /logs killtick DROPS below ~460 and our-E stays
  high. If enemy becomes a genuine constant-velocity mover, RAISE the linear
  weight (lower W toward 0.5-0.6); re-run the replay sim to retune W & power.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = pez__gf1 (FIRST STRONG FOE)

## KEY FINDING: opponent is a strong MOBILE dodger with a GuessFactor gun ("gf1")
Analyzed /logs/rounds/0 (opponent pez__gf1). This is our first genuinely tough
opponent. Prior result BEFORE my change: opus-4-8 24924 vs pez__gf1 2574.
results_0.txt: opus 1027 (96%) vs pez 41 (4%), 10/10 firsts BUT:
- trace.md: our win rate 90% (226/250), enemy 8%. Our accuracy only 12%,
  enemy 3%. Avg speed us 5.5, enemy 4.8. We LOSE 19 games.
- Enemy movement: avg |v|=4.75 (max 8), stopped only 25% of ticks, curves
  (avg |dh|=0.028 rad/tick). A real orbiter/oscillator — NOT stationary.
- In losses (e.g. sim_112) we deal only ~37 dmg while taking ~100: enemy's GF
  gun profiles our steady orbit and out-trades us. Our min energy avg = 32.8.

## Replay-sim results (per-tick interception over recorded enemy paths, 60 sims)
Tool: /tmp/replay.py (rebuild from README if lost). All aim strategies cap
~11-13% hit vs this dodger; head-on = 13.0% = BEST (blend0.9 ties it).
Power sweep: power 3.0 head-on maximizes total damage (56k) — dmg/hit dominates.
Distance-adaptive power measured WORSE. => GUN LEFT as-is (blend W=0.90 ~= head-on,
power 3.0). Accuracy is capped by enemy evasion, not our aim; don't over-tune gun.

## CHANGE THIS PASS: MOVEMENT (the real lever vs a GF gun)
Rewrote doMovement to be less profileable:
1. Reverse ~50% of the time on detected enemy fire (rate-limited >=6 ticks) so
   (energy drop 0.09..3.05), min 4 ticks between reversals — classic anti-GF
   "reverse at wave-fire" dodge, randomized so it's not itself a pattern.
2. Periodic random reversal (10% chance, >=12 ticks apart) to break steady orbit.
3. setAhead 100 -> 150 (keep higher lateral speed between reversals).
4. Range control widened (pull in >500px, push out <300px).
Rationale: our steady 3.3%/tick direction-change orbit was being learned by the
GF gun (that's why we lost the trade in losses). More unpredictable lateral
motion should cut enemy hits and reduce our 19 losses / raise survival energy.
Compiles Java 8 (major version 52). Backup: /tmp/MyTank.bak.java (also git).

## CAVEAT: cannot verify locally (harness broken per prior notes). This is a
## reasoned anti-GF movement change. If NEW /logs shows our win rate DROP below
## 90% or min-energy fall, REVERT to /tmp/MyTank.bak.java (git prior version) —
## the old movement still wins 90%. If win rate improves, keep tuning reversal
## probability (0.75) and periodic reversal rate (0.10).

## Replay tool rebuild (analysis) — head-on interception hit-rate per opponent:
See /tmp/replay.py this pass; core loop: load sim (per-file header maps
index->name, enemy = non-'opus'), for each tick fire bullet from our recorded
(x,y) along aim, step at 20-3*power, hit if dist<18 to enemy's recorded future pos.

## REFINEMENT (same pass): decoupled reversals from strict enemy-fire alternation
Changed reversal logic to avoid becoming a learnable alternation at the enemy's
fire cadence: (a) reverse only 50% on enemy-fire (>=6 ticks apart); (b) random
6% periodic reversal (>=8 ticks apart, avg segment ~16 ticks). Keeps setAhead=150.
Final compile: Java 8 major version 52, clean. This is the version submitted.

# Agent Notes (Round 2 / this pass) — opponent = pez__gf1 (GuessFactor gun)

## STATUS this match: winning ~93% (round1: 25200 vs 2156; 9/1 firsts in results_0)
Opponent = strong MOBILE dodger with a GuessFactor gun. We win the trade because
enemy accuracy ~2% vs our ~12%, but we still LOSE ~16/250 games (variance: in
losses the enemy moves faster avg|v|~5 and profiles our orbit; we take 35-49
hits vs 18-31 in wins). Wall-proximity is NOT the cause (losses have LESS wall
time). Losses are GF-gun learning variance in fast-mover games.

## CHANGE THIS PASS: gun -> pure head-on (W 0.90 -> 1.00)
Rebuilt /tmp/replay.py (per-tick interception over recorded enemy paths, ALL
250 sims of round 1). CORRECTED earlier bug: field is 800x600 (not 1000) and
enemy heading field is 'bh' (radians), velocity 'v'. Results (power 3.0):
  headon(W1.0)=27.1%  W0.9=22.9%  W0.7=20.0%  W0.5=19.5%  W0.0(lin)=18.0%  circ=18.5%
Head-on is MONOTONICALLY best — enemy dodges reactively so ANY lead overshoots.
Power sweep (head-on) dmg/round: p1.0=54, p1.9=113, p2.4=136, p3.0=159 -> keep 3.0.
So: W=1.0, power=3.0. This raises hit rate ~4pts -> more bullet dmg + faster
kills -> less exposure -> should convert some marginal losses.
Compiles Java 8 (major version 52). Backup of prior version: /tmp/MyTank.bak.java.

## Movement: LEFT UNCHANGED (deliberate)
Considered periodic sin-wobble on orbit angle but REJECTED it — a periodic
signal is itself learnable by a GF gun (counterproductive). Considered stop-and-go
but it lowers survival speed/bonus and adds a learnable low-velocity bin; can't
validate locally so too risky. Current orbit + randomized wave-reversal already
wins 93%. If a next pass wants the real fix it's WAVE SURFING (track enemy
bullet waves, move to min-danger GF) — the only robust anti-GF movement, but
needs careful implementation + local validation (harness is broken here).

## Replay tool: /tmp/replay.py (NOT persistent across rounds — rebuild from this note)
Loads /logs/rounds/1/sim_*.jsonl; per-file header maps index->name (enemy = the
non-'opus' one). For each tick fires a bullet from OUR recorded (x,y) along an aim
strategy, steps at speed 20-3*power, hit if dist<18 to enemy's recorded future pos,
respects 800x600 bounds + gunheat cooldown. Use it to retune W/power per opponent.
CAVEAT: biased (enemy path was reactive to our ACTUAL shots) but head-on's large
consistent lead over blends is trustworthy.

## Hard requirement reminder
Keep class name MyTank + compile to Java 8:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52

# Agent Notes (Round 1 / current pass) — opponent = linuxuser0__genetic

## STATUS: PERFECT WIN (100% win rate, 99% score share) — NO CODE CHANGE
Verified /logs/rounds/0:
- results.json: opus-4-8 44810 vs linuxuser0__genetic 328.
- results_0.txt: opus_4_8.MyTank 1801 (99%), 10/10 firsts; enemy 20 (1%).
- trace.md: our win 100% (250/250), accuracy 38%, avg speed 5.3, avg min E 77.
  Enemy: 0% win, 2.6 shots/game, 14% accuracy, dies avg turn 323.

## Opponent behavior: VARIABLE mobile bot (not stationary)
Per-sim analysis (header maps index->name; enemy = non-'opus', here i=0):
- Averaged ~40% of ticks moving; our avg final energy ~105, avg kill tick ~358.
- BUT high variance: in worst games (sim_104/105/108/64) the enemy dodges at
  FULL speed (v=8, ~78% of ticks) and lands many hits — we drop to ~11-16 energy
  but STILL WIN all 250. So the genetic bot occasionally behaves like a strong
  mobile dodger; our pure-head-on gun (W=1.0) + orbital/anti-GF movement handles
  it every time.

## Decision: NO gameplay change
We already score essentially the maximum (survival + bonuses; only 1% leaks to the
enemy via ~20 bullet dmg). Faster kills wouldn't raise score share meaningfully,
and any edit risks regression on the 250-game sample we currently sweep. Left
MyTank.java unchanged (pure head-on W=1.0, power 3.0). Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## Worst-game finder (reusable — spot games where WE take heavy damage)
cd /logs/rounds/<N> && python3 -c "
import json,glob
w=[]
for fn in sorted(glob.glob('sim_*.jsonl')):
    ls=[json.loads(l) for l in open(fn) if l.strip()]
    hdr=ls[0]['robots']; ei=[int(k) for k,v in hdr.items() if 'opus' not in v][0]; oi=1-ei
    fe=100
    for d in ls:
        if 'u' in d:
            for u in d['u']:
                if u['i']==oi: fe=u['e']
    w.append((fe,fn))
w.sort(); print(w[:8])"

## For next teammate
- Only act if a NEW /logs shows win rate <100% or our min-energy collapsing in a
  loss. This genetic bot may EVOLVE between rounds (name suggests genetic algo);
  if it becomes a consistently strong dodger, the next real lever is WAVE SURFING
  for movement (only robust anti-GF evasion) — but validate carefully (local
  harness is broken; trust /logs). Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 / current pass) — opponent = linuxuser0__genetic

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE
Verified /logs/rounds/{0,1}: opus-4-8 44810/44794 vs linuxuser0__genetic 328/399.
results_0.txt both rounds: opus_4_8.MyTank 1801/1783 (99%), 10/10 firsts each.
Enemy leaks only ~20 bullet dmg (1% share). This is essentially MAX score.

## Opponent behavior (unchanged from round-0 notes): VARIABLE mobile bot
Worst-game check on round 1 (our final energy): [15, 24, 24.5, 26, 31, ...].
In those (sim_155/157) the enemy dodges at FULL speed (v=8, moving 62-80% of
ticks) but STILL DIES (enemy final E = 0). We win all 250/250 regardless.

## Decision: NO gameplay change (deliberate)
We already score the theoretical max. Faster kills wouldn't raise the 99% share.
Any movement/gun edit risks regression on a 250-game sweep we currently win 100%,
and the local harness is broken (can't validate). Pure head-on gun (W=1.0, power
3.0) + anti-GF orbital movement handles even full-speed dodgers here.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). OK.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our min-energy collapsing to a
LOSS (enemy final E > 0 while ours = 0). If the genetic bot evolves into a
consistently strong dodger, the real lever is WAVE SURFING (validate carefully).
Keep MyTank class name + Java-8 bytecode (only hard requirement).
Worst-game finder + movement-check one-liners are in earlier notes above.

# Agent Notes (Round 1 / current pass) — opponent = kinnla__antiwalls

## STATUS: PERFECT WIN — NO CODE CHANGE
Verified /logs/rounds/0:
- results.json: opus-4-8 44999 vs kinnla__antiwalls 67.
- results_0.txt: opus_4_8.MyTank 1800 (100%), 10/10 firsts; enemy 0 (0%).
- trace.md: our win 100% (250/250), accuracy 66%, avg speed 5.6, avg min E 87.
  Enemy: 0% win, 1.0 shot/game, 2% accuracy, dies avg turn 192, speed 0.9.

## Opponent behavior: NEAR-STATIONARY wall-hugger ("antiwalls")
Per-sim analysis (first 20 sims): enemy moves only ~14% of ticks (frac 0.143).
Our final energy avg ~127, kill tick avg ~200. Essentially a sitting duck that
occasionally nudges. Our pure head-on gun (W=1.0, power 3.0) lands 66% -> fast
kills, near-zero damage taken. This is the MAX possible score share.

## Decision: NO gameplay change (deliberate)
1800/1800, 100% score share, 10/10 firsts = theoretical maximum. Faster kills
would NOT raise the score. Any gun/movement edit only risks regression on a
250/250 sweep we currently win. Left MyTank.java unchanged.
Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our min-energy collapsing to a
loss (enemy final E > 0 while ours = 0). If antiwalls becomes a strong mobile
dodger, the real lever is WAVE SURFING (validate carefully; local harness broken).
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = kinnla__antiwalls

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE
Verified /logs/rounds/{0,1}: opus-4-8 44999/45009 vs kinnla__antiwalls 67/152.
results_0.txt both rounds: opus_4_8.MyTank 1800 (100%), 10/10 firsts each.
Enemy = near-stationary wall-hugger (moves ~14% of ticks). This is MAX score.

## Decision: NO gameplay change (deliberate)
1800/1800 = theoretical maximum (survival + all bonuses). Faster kills would NOT
raise score. Any edit risks regression on a 250/250 sweep we currently win.
Pure head-on gun (W=1.0, power 3.0) lands ~66% -> fast kills, near-zero dmg taken.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0. OK.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = barriosnahuel__tirolio

## KEY FINDING: opponent is a FULL-SPEED, ENERGY-CONSERVING DODGER (first foe we were LOSING to)
This is the first opponent that BEATS us. Prior result BEFORE my change:
- results.json: opus-4-8 26747 vs tirolio 8778 (only 75% share).
- trace.md: WE WIN ONLY 42% (104/250). Enemy wins 58% (146/250)!!
- results_0.txt (a 10-round battle): 1585 vs 125, 8/10 firsts — MISLEADING; the
  full 250-sim trace is the truth: we lose the majority.

## WHY WE LOST: we shot ourselves to death
Enemy fires ~0.4 shots/GAME (accuracy 0%) — it barely attacks. It moves 83% of
ticks at up to v=8 (full speed) and DODGES. Our OLD gun = pure head-on (W=1.0),
power 3.0, firing constantly -> only ~10-13% hit vs this mover. Every power-3
miss costs 3 energy. By tick ~600 our energy was 18 while enemy sat at 70-84
(near-zero damage taken). We drained to 0 and died; enemy survived w/ avg 44.7 E.
It's a pure ENERGY-WAR / survival strategy: let the aggressor waste its energy.

## FIX (this pass): energy-aware low-power gun + closer orbit + better lead
Replay sim (/tmp/replay.py, rebuilt) over recorded enemy paths showed:
  * Aim: W=0.5 (50% current + 50% linear lead) = 26.7% hit vs 9.9% head-on.
    It moves at ~constant velocity so a REAL lead is needed (unlike prior
    reactive stop-and-go foes where head-on won). Half-lead beats full lead
    because its reversals make a full lead overshoot.
  * Hit rate by distance: near(<250)=61%, mid(250-450)=37%, far(>450)=19%.
  * NET ENERGY per shot (cost -p, gain +3p on hit): power1.0=+0.11, power1.5=+0.05,
    power3.0=-0.60. => LOW power (far) GAINS energy; HIGH power only pays up close.
Changes in MyTank.java:
  1. aimAndFire: W=1.0 -> W=0.5; power now distance-tiered
     (<200:3.0, <300:2.4, <450:1.6, else:1.0). Cap power<=1.2 if our E < enemy E
     (stay net-positive in the energy war). Hold far(>550) shots if E tight.
  2. doMovement range control: orbit CLOSER (~280px; pull in >350, push out <200)
     to raise hit rate (61% near vs 19% far). Kept anti-GF randomized reversals.

## VALIDATION (energy-war replay sim, 120 games — see /tmp snippet in git/this note)
  OLD(head-on pw3): ourFinalE avg 7.3 (DEAD), enemyDmg 79 (<100 kill), kills 36/120.
  NEW(W0.5 adaptive): ourFinalE avg 119.8 (we GAIN E!), enemyDmg 700, kills 120/120.
This should flip the match from a 42% LOSS to a strong win. (Sim is optimistic —
enemy path was reactive to our OLD shots — but the direction is overwhelming.)
Compiles Java 8 (major version 52). Backup of prior version: /tmp/MyTank.bak.java.

## For next teammate
- If NEW /logs still shows <50% win: enemy may adapt. Re-run /tmp/replay.py to
  retune W & the distance power tiers. The core principle vs an energy-conserving
  dodger: NEVER fire net-negative-energy shots (keep power low unless hit rate is
  high, i.e. close range). Consider WAVE SURFING only if enemy starts firing more.
- If enemy becomes stationary in some rounds: W=0.5 degrades gracefully (lead=0).
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = barriosnahuel__tirolio

## STATUS: THE FIX WORKED — we FLIPPED from a 42% LOSS to a 100% WIN
Round 0 (BEFORE the prior teammate's energy-aware gun fix): opus 26747 vs
tirolio 8778, we won only 42% of the 250-sim trace (the energy-war loss).
Round 1 (AFTER the fix): opus 44996 vs tirolio 13. results_0.txt = 1799 (100%),
10/10 firsts. trace.md: WIN RATE 100% (250/250), accuracy 40%, avg min E 90.
Worst game: we finished 47.8 E while enemy DIED (0.0). Avg our final E = 114.4.
Enemy dies in EVERY one of 250 games. This is essentially the theoretical max.

## Decision this pass: NO code change (deliberate)
The energy-aware, distance-tiered low-power gun (W=0.5 half-lead, power tiers
3.0/2.4/1.6/1.0 by distance, cap 1.2 when ourE<enemyE) + closer orbit (~280px)
that the prior teammate added is DATA-OPTIMAL vs this energy-conserving dodger.
It converts the energy war in our favor (we now GAIN energy while it wastes its
few shots and eats ours). Score is 100% share; faster kills wouldn't raise it.
Any gun/movement edit only risks regression on a 250/250 sweep we now win.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate
Only act if a NEW /logs shows win rate dropping below 100% or our min-energy
collapsing to a LOSS (enemy final E > 0 while ours = 0). If tirolio adapts to
close-range (starts firing more / ramming), the next lever is WAVE SURFING for
movement, or lowering the close-range power if it starts dodging our net-positive
shots. Re-run the replay-sim principle to retune W & power tiers.
Keep MyTank class name + Java-8 bytecode (only hard requirement).
Worst-game / movement one-liners are in earlier notes above.

# Agent Notes (Round 1 / current pass) — opponent = pez__droidpoet

## STATUS: 100% WIN (250/250), 97% score share — TUNED GUN FOR MORE MARGIN
Round 0 result: opus-4-8 46753 vs pez__droidpoet 1729. results_0.txt: 1876 (97%),
10/10 firsts. Enemy = ACTIVE full-speed MOBILE dodger (avg|v|=4.4, moving 63% of
ticks, curves a lot avg|dh|=0.078). It FIRES back (16.8 shots/game, 8% acc) and
starts at 120 energy. We win every game, enemy dies every game (finalE 0).
BUT close games exist: worst sim_138 we finished with only 2.6 energy; avg ~87.

## Replay-sim findings (/tmp/replay2.py, /tmp/dmgsim2.py — rebuild from git/these notes)
Per-tick interception over recorded droidpoet paths (800x600, enemy heading='bh'):
- Aim: this is a near-CONSTANT-VELOCITY mover -> FULL LINEAR LEAD wins.
    W=0.0 (full lead)=20.9% hit, W=0.5 (old)=17.6%, W=1.0 headon=18.6%.
  (Opposite of the reactive stop-and-go foes where head-on won! Verify aim per
   opponent with the replay sim.)
- Hit rate by distance: <200px=61%, 200-300=39%, 300-450=27%, 450-600=16%, far=12%.
- Damage/round (distance-tiered power 3.0/2.4/1.6/1.0): W=0.5=75 -> W=0.0=90 (+19%).

## Changes this pass (MyTank.java)
1. Aim: W 0.5 -> 0.0 (full linear lead), and made the lead ITERATIVE (12 passes)
   instead of single-pass for accuracy.
2. Power cap: REMOVED the `getEnergy() < enemyEnergy -> power<=1.2` energy-war cap
   (droidpoet starts at 120E so that cap was throttling us to 1.2 power all game
   even up close where hit rate is 61%!). Replaced with absolute low-E safety:
   <30->2.0, <15->1.0, <6->0.4. We beat this bot 100% so max damage = faster
   kills = more margin in the close games. NOT an energy-conserving passive foe.
Compiles Java 8 (major version 52), 0 errors. Backup of prior version: /tmp/MyTank.bak.java

## CAVEAT: local harness broken (per all prior notes) — trust /logs, not local battles.
The replay sim is biased (enemy path was reactive to our OLD shots) but the W=0.0
lead advantage is consistent and the direction (more damage, faster kills) is safe
given we already win 100%.

## For next teammate
- If NEW /logs shows win rate <100% or our min-energy collapsing to a LOSS, revert
  to /tmp/MyTank.bak.java (git) which won 100% with the old conservative gun.
- If droidpoet becomes a reactive stop-and-go dodger (check avg|dh| & moving frac),
  raise W back toward 0.5-1.0. Re-run /tmp/replay2.py to retune W & power tiers.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = pez__droidpoet

## STATUS: 100% WIN (250/250), score share rose 97%->98% after round-1 gun change
Verified /logs/rounds/{0,1}: opus-4-8 46753(97%)/48300(98%) vs droidpoet 1729/989.
Round 1 (prior teammate's W=0.0 full-linear-lead gun) worked: our worst-game
final energy jumped from 2.6 (round 0) to 80.4 (round 1); avg final E 116.4;
kill tick avg 485. We win every game, enemy dies every game.

## Opponent = near-CONSTANT-VELOCITY full-speed mover
Round-1 sim analysis: moving frac 0.67, avg|v| 4.47, avg|dh| 0.0117 (almost
straight-line). Full linear lead (W=0.0) confirmed optimal (per prior replay-sim
20.9% vs 17.6% half-lead). Kept W=0.0.

## CHANGE THIS PASS: power tiers raised (tier C) for more bullet damage
Replay-sim (/tmp/quick.py, 120 sims) over recorded droidpoet paths, W=0.0:
  old tiers (200:3.0/300:2.4/450:1.6/else:1.0): 98.1 dmg/game, 29.5% hit
  NEW tier C (200:3.0/350:2.5/500:1.8/else:1.2): 111.9 dmg/game (+14%), 27.1% hit
  (higher power/hit dominates the small hit-rate drop). Tested 'allmax' (power
  3.0 always): 127.9 dmg but sim min-energy 1.0 = too risky (sim ignores enemy
  damage). Tier C keeps sim min-E ~35 (real games much safer, worst was 80.4).
Since we win 250/250 with 80+ E to spare, raising damage = more score share, safe.
Compiles Java 8 (major version 52). Backup of prior version: /tmp/MyTank.bak.java.

## Replay tool: /tmp/quick.py (rebuild from this note if lost)
Loads /logs/rounds/1/sim_*.jsonl (per-file header maps index->name; enemy=non-
'opus'). For each tick fires bullet from OUR recorded (x,y) with linear lead,
steps at 20-3*power, hit if dist<18 to enemy future pos, respects 800x600 +
gunheat. Full version /tmp/replay.py also has an energysim() approximating our
net energy (fire cost - hit gain, ignoring enemy dmg -> optimistic).

## For next teammate
- If NEW /logs shows win rate <100% or min-energy collapsing, REVERT to
  /tmp/MyTank.bak.java (git prior) or lower tier C back toward old tiers.
- If droidpoet changes to a reactive stop-and-go dodger (check avg|dh| & moving
  frac), raise W toward 0.5-1.0 and re-run /tmp/quick.py to retune W & power.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = robo_code__crazy

## KEY FINDING: opponent is the "Crazy" sample bot (wall-bouncing wide-arc mover)
Analyzed /logs/rounds/0 (opponent robo_code__crazy). Prior result BEFORE my change:
- results.json: opus-4-8 38430 vs crazy 1352 (92% share in results_0.txt).
- trace.md: WIN RATE 100% (249/250) but games are LONG (avg 673 turns), our
  accuracy only 22%, avg min energy 60. We LOST 1/250 (sim_1): a 1340-turn
  nail-biter ending our E 0.0 vs enemy 0.2.
- Enemy: avg|v|=7.09 (near full speed), moves 95% of ticks, curves hard
  (avg|dh|=0.068), hits walls ~7.3x/game. Classic "Crazy" bot: drives in wide
  arcs and bounces off walls.

## GUN REWRITE: full-linear-lead (W=0.0) was TERRIBLE here -> switched to HEAD-ON
The prior gun (tuned for pez__droidpoet's constant-velocity path) used W=0.0 full
linear lead + distance power tiers. That is the WORST aim vs this wall-bouncer.
Replay-sim (/tmp/rep2.py) per-tick interception over 100 recorded crazy paths
(800x600, enemy heading='bh', wall-clamped predictions):
  HEAD-ON (W=1.0): 27.8% hit, 4.45 dmg/shot  <-- BEST
  W=0.9: 23.4/3.74   W=0.5: 21.7/3.47   circular+turn: 18.3/2.92
  W=0.0 (OLD): 13.3% hit, 2.12 dmg/shot  <-- what we were using!
The enemy curves + bounces so much that ANY lead overshoots; aim at current pos.
Power: flat 3.0 maximizes dmg/shot (tiering LOWERED it: tierA 3.99, flat3 4.45).
We win 100% w/ energy to spare, so max power = max bullet damage = more score.

## Changes made (robots/custom/MyTank.java)
1. aimAndFire: power tiers -> flat power = 3.0 (kept low-E safety clamps
   30->2.0, 15->1.0, 6->0.4 as anti-self-destruct nets).
2. Aim blend: W 0.0 -> 1.0 (head-on). Lead-prediction loop still runs but W=1.0
   makes predX/predY = current enemy pos.
This ~doubles our hit rate (13.3% -> 27.8%) => far more bullet damage + faster
kills => should convert the 1 loss and boost our 92% score share.
Compiles Java 8 (major version 52), rc=0. Backup: /tmp/MyTank.bak.java (also git).

## Replay tool: /tmp/rep2.py (rebuild from this note if lost)
Loads /logs/rounds/0/sim_*.jsonl (per-file header maps index->name; enemy=non-
'opus'). For each tick fires a bullet from OUR recorded (x,y) along an aim (W blend
of head-on vs iterative linear lead), steps at 20-3*power, hit if dist<18 to
enemy's recorded future pos, 800x600 bounds. Samples every 2nd tick for speed
(30s cmd timeout). CIRC prediction is in /tmp/rep.py.

## For next teammate
- If crazy reappears: head-on W=1.0 flat power 3.0 is data-optimal; verify NEW
  /logs killtick DROPS and score share rises above 92%. If enemy becomes a
  straight-line constant-velocity mover, RAISE lead (lower W toward 0.0) and
  re-run /tmp/rep2.py. If it becomes stationary, head-on still optimal.
- Keep MyTank class name + Java-8 bytecode (only hard requirement):
    javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java
    javap -v robots/custom/MyTank.class | grep "major version"  # -> 52

# Agent Notes (Round 2 / current pass) — opponent = robo_code__crazy — REGRESSION FIX

## CRITICAL: round 1 REGRESSED from 100% to 83% — REVERTED THE GUN
This opponent = "Crazy" sample bot (fast wall-bouncing wide-arc mover, avg|v|~7,
moves 95% of ticks, hits walls ~7.7x/game, curves hard).

Real /logs results for THIS match:
- ROUND 0 (played with droidpoet-r2 gun: W=0.0 full lead + distance-tiered power
  3.0/2.5/1.8/1.2): WON 100% (249/250), score 38430 vs 1352 (92% share),
  our avg min energy 60.
- ROUND 1 (prior teammate changed gun to HEAD-ON W=1.0 + FLAT power 3.0 based on
  a BIASED replay sim): REGRESSED to 83% (207/250, 43 losses, 11 draws!),
  score 34327 vs 4196 (enemy tripled its score), our avg min energy dropped 60->34.
  WHY: firing power-3 constantly at a fast dodger with ~16% real hit rate DRAINS
  us in the energy war. In losses the enemy still had 40-50 E while we died at 0.
  The replay sim was biased (enemy path was reactive to our OLD tiered-power shots)
  and over-stated head-on's hit rate; the flat-power drain was the real effect.

## FIX THIS PASS: reverted gun to the round-0 100%-win config
- Aim W = 0.0 (full linear lead) — the config that WON 100%.
- Power = distance-tiered 3.0(<200)/2.5(<350)/1.8(<500)/1.2(else) — keeps far
  shots net-positive so we don't bleed energy vs this dodger.
- Verified: code is FUNCTIONALLY IDENTICAL (comments aside) to git 1070275's
  MyTank.java, which is the exact version that won round 0 at 100%.
- Movement UNCHANGED (it was fine at 100% in round 0). Do NOT touch it.
- Compiles Java 8, major version 52. Backup of round-1 (bad) version: /tmp/MyTank.bak.java.

## LESSON: distrust the replay sim's absolute hit-rate numbers
The replay sim uses the enemy's RECORDED path, which was REACTIVE to our actual
shots — so it cannot fairly compare a different gun. The only trustworthy signal
is the REAL /logs win rate. Round 0 (tiered power) = 100%; round 1 (flat power) =
83%. That is decisive. Flat power 3.0 is a trap vs any energy-efficient fast
dodger: net energy per power-3 shot at ~16% hit is strongly NEGATIVE.

## For next teammate
- KEEP this gun config unless a NEW /logs shows win rate <100%. If it drops,
  first suspect any power increase. Never go flat power 3.0 vs a fast dodger.
- The one round-0 loss (sim_1, 1340 turns) was a rare long energy-war grind; the
  tiered gun still won 249/250. Movement/wave-surfing is the only further lever
  but it's high-risk — validate carefully (local harness is broken; trust /logs).
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = it_economics__ite_claptrap

## STATUS: PERFECT WIN — NO CODE CHANGE
Verified /logs/rounds/0:
- results.json: opus-4-8 44915 vs it_economics__ite_claptrap 236.
- results_0.txt: opus_4_8.MyTank 1792 (100%), 10/10 firsts; enemy 0 (0%).
- trace.md: our win 100% (250/250), accuracy 35%, avg speed 5.8, avg min E 83.
  Enemy: 0% win, 1.5 shots/game, 9% acc, dies avg turn 443, hits walls 12.7x/game.

## Opponent behavior: near-CONSTANT-VELOCITY wall-crashing mover
Per-sim analysis (header maps index->name; enemy = non-'opus'):
- Moves 82% of ticks, avg |v| 4.81, avg |dh| ONLY 0.022 rad/tick (near straight
  lines) -> full linear lead (W=0.0) is data-optimal. Crashes walls 12.7x/game.
- Our worst-game final energy = 52.1 (NO close games); avg final E 102.9;
  avg kill tick 443. This is essentially the theoretical maximum score.

## Decision: NO gameplay change (deliberate)
Current gun (W=0.0 full linear lead, distance-tiered power 3.0/2.5/1.8/1.2 + low-E
safety clamps) is EXACTLY the config that just won 100% here and matches the
constant-velocity mover profile. Any edit only risks regression on a 250/250
sweep we already win with 50+ E to spare. Left MyTank.java unchanged.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our min-energy collapsing to a
loss (enemy final E > 0 while ours = 0). If claptrap becomes a reactive stop-and-go
dodger (check avg|dh| & moving frac), raise W toward 0.5-1.0. Never go flat power
3.0 vs a fast dodger (that regressed us to 83% vs robo_code__crazy).
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 replay pass) — opponent = it_economics__ite_claptrap

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE
Verified /logs/rounds/{0,1}: opus-4-8 44915/44835 vs claptrap 236/196.
results_0.txt: opus_4_8.MyTank 1792/1796 (100%), 10/10 firsts each round.
Worst-game check on round 1: our worst final energy = 46.7 while enemy DIES.
LOSSES: 0/250. No close games. This is the theoretical max score share.

## Opponent = near-constant-velocity wall-crashing mover (unchanged profile)
Current gun (W=0.0 full linear lead + distance-tiered power 3.0/2.5/1.8/1.2 +
low-E safety clamps) matches its profile and is data-optimal. Left MyTank.java
UNCHANGED — any edit only risks regression on a 250/250 sweep we win with 46+ E
to spare. Re-verified compile: javac --release 8 ... -> major version 52 (Java 8).

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
Never go flat power 3.0 vs a fast dodger (regressed us to 83% vs robo_code__crazy).
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 4 / this round)

## Opponent CHANGED: it_economics__ite_ctbot
Previous rounds faced various dodgers. This round's opponent (from
/logs/rounds/0) is a SLOW, LIGHTLY-CURVING mover:
- 28% of ticks stationary, spread across all speeds, RARELY full speed (0.9%).
- Avg turn rate only ~0.23 deg/tick (nearly linear).
- It LOSES the energy war to us (we won 249/250, avg 98.6 vs 0.1 final energy).

## Changes this round (BOTH replay-sim verified over 60 games)
1. Gun lead weight W: 0.0 -> 0.5 (half-lead). This opponent's slow curve means
   full linear lead OVERSHOOTS. Half-lead hits 41.3% vs 33.8% for W=0.0.
2. Power tiering raised: was 3.0/<200, 2.5/<350, 1.8/<500, 1.2/else.
   Now 3.0/<350, 2.5/<550, 2.0/else. Replay-sim: EVERY power tier is
   net-energy-POSITIVE vs this bot (power3.0 = +2.59 E/shot, 34.9% hit).
   Higher power = more damage AND more net energy since we win the energy war.

## Replay-sim result (60 games, constant-fire model)
   OLD (W=0.0, low far power): 28572 dmg, net +11502
   NEW (W=0.5, high power):    42211 dmg, net +20406  (+48% dmg, +77% net)

## Analysis script used (best-W and per-power hit rate)
See the python one-liners in the round-4 git commit / step history: they replay
each recorded enemy path, fire a simulated bullet with weight W lead at power P,
and check for a <20px hit along the enemy's ACTUAL future trajectory.

## Compilation (unchanged, ALWAYS Java 8)
   javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java
   javap -v robots/custom/MyTank.class | grep "major version"   # want 52

## Recommendation for next teammate
Verify the win rate stays ~100% in the new sim logs. If the opponent changes
AGAIN, re-run the W-sweep + per-power-net replay to retune W and power. The gun
predictor is linear-only; if a hard-curving opponent appears, add circular lead
(track per-tick heading delta and apply in the 12-iter predictor loop).

# Agent Notes (Round 2 verification pass) — opponent = it_economics__ite_ctbot

## STATUS: THE ROUND-1 GUN CHANGE WORKED — 100% WIN, NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (this match, opponent it_economics__ite_ctbot):
- Round 0 (before gun tune): opus 44394 vs ctbot 831; win rate 99.6% (249/250),
  accuracy 35%, 28.0 shots/game, enemy avg death turn 435.
- Round 1 (W=0.5 half-lead + power tiers 3.0/<350, 2.5/<550, 2.0/else): opus
  44529 vs ctbot 427; win rate 100% (250/250), accuracy 40%, only 18.5 shots/game,
  enemy avg death turn 318 (FASTER kills), enemy score HALVED (831->427).
- results_0.txt both rounds: opus_4_8.MyTank 1788 (99%), 10/10 firsts.

## Worst-game check on round 1 (our final energy, enemy final energy)
worst 6: (52.0,sim_202,enemy0.0)(58.0,sim_167,0.0)(58.0,sim_94,0.0)... — NO close
games. Mean our final E = 106.6, enemy DIES every game (finalE 0.0). Mean killtick
318. This is the theoretical maximum (survival + bonuses; only ~1% leaks via a
couple enemy bullet hits, unavoidable without wave surfing).

## Opponent = SLOW, LIGHTLY-CURVING mover (unchanged profile)
Current gun (W=0.5 half-lead, power tiers 3.0/2.5/2.0, low-E safety clamps) is
data-optimal per prior replay-sim (half-lead 41% hit vs 34% full-lead; every power
tier net-energy-positive since ctbot loses the energy war to us).

## Decision: NO code change (deliberate)
Source is IDENTICAL to the round-1 winning commit (2e1f702). Verified via
`git diff robots/custom/MyTank.java` = empty. Any gun/movement edit only risks
regression on a 250/250 sweep we win with 50+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). If ctbot changes profile, re-run the W-sweep +
per-power-net replay to retune W and power. Never go flat power 3.0 vs a FAST
dodger (that regressed us to 83% vs robo_code__crazy). Keep MyTank class name +
Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = it_economics__ite_simple

## STATUS: 100% WIN (250/250), 99% share — TUNED GUN W 0.5 -> 0.25
Round 0 result: opus-4-8 44903 vs it_economics__ite_simple 184. results_0.txt:
opus_4_8.MyTank 1798 (99%), 10/10 firsts; enemy 12 (1%). Zero losses, zero close
games (worst our-final-E = 18.5 while enemy DIES; mean much higher). Enemy is a
wall-crashing NEAR-CONSTANT-VELOCITY mover: moving 85% of ticks, avg |v| 4.24,
turn only 0.0134 rad/tick (nearly straight lines), 18.6 walls/game.

## Change this pass: gun lead weight W 0.5 -> 0.25
Replay-sim (/tmp/replay.py, per-tick interception over recorded enemy paths, run
on TWO independent 80-game slices for robustness):
  slice A: W0.0=16.9% W0.25=24.1% W0.5=21.1% W0.75=20.2% W1.0=15.7%
  slice B: W0.0=19.7% W0.25=30.7% W0.5=23.6% W0.75=23.3% W1.0=18.5%
Clean, consistent peak at W=0.25 for this fast straight mover (the prior W=0.5
was tuned for the SLOWER, curving ctbot). Higher hit rate = faster kills = more
bullet dmg/bonus, and we win the energy war so there's no survival downside.
Power tiers UNCHANGED (3.0/<350, 2.5/<550, 2.0/else + low-E safety clamps) —
already net-energy-positive vs this bot which loses the energy war to us.
Movement UNCHANGED. Backup of prior source: /tmp/MyTank.bak.java (also git).
Compiles Java 8 (major version 52), rc=0.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
If the opponent's turn rate rises (curving dodger), raise W back toward 0.5; if it
becomes a perfectly straight constant-velocity mover, lower W toward 0.0. Re-run
/tmp/replay.py W-sweep on >=2 slices to confirm before changing. Never go flat
power 3.0 vs a FAST dodger (that regressed us to 83% vs robo_code__crazy).
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass) — opponent = it_economics__ite_simple

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (this match, opponent it_economics__ite_simple):
- Round 0: opus 44903 vs simple 184. results_0.txt: 1798 (99%), 10/10 firsts.
- Round 1 (W=0.25 gun tune from prior teammate): opus 44907 vs simple 172.
  trace.md: WIN RATE 100% (250/250), accuracy 33%, avg min E 77. Enemy 0/250,
  dies avg turn 387, 18.5 walls/game (wall-crashing constant-velocity mover).

## Worst-game check (round 1): 0 losses
worst-6 (ourE,file,enemyE): (13.5,sim_152,0.0)(19.0,sim_159,0.0)(20.0,sim_111,0.0)
... — enemy DIES every game (finalE 0.0). No losing games. Theoretical max share.

## Decision: NO code change (deliberate)
Source is the round-1 winning config (W=0.25 half-lead, power tiers 3.0/<350,
2.5/<550, 2.0/else + low-E safety clamps, orbit ~280px). Matches this fast
straight-line mover's profile (turn ~0.013 rad/tick). Any edit only risks
regression on a 250/250 sweep we win with 13+ E to spare in the worst game.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). If ite_simple's turn rate rises (curving),
raise W toward 0.5; if perfectly straight, lower toward 0.0 (re-run /tmp/replay.py
W-sweep on >=2 slices first). Never go flat power 3.0 vs a FAST dodger (regressed
us to 83% vs robo_code__crazy). Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 1 / current pass) — opponent = it_economics__ite_terminator

## STATUS: 100% WIN (250/250), 98% share — TUNED GUN W 0.25 -> 0.85
Round 0 result: opus-4-8 44105 vs it_economics__ite_terminator 801. results_0.txt:
opus_4_8.MyTank 1767 (98%), 10/10 firsts. Zero losses, zero close games (worst our
final-E = 58.5 while enemy DIES; mean final-E 106.8, mean killtick 306).

## Opponent behavior: SLOW, lightly-curving mover
Per-sim analysis: moving ~51% of ticks, avg |v| ONLY 2.56, turn 0.026 rad/tick,
avg speed 2.6 (trace.md). Fires ~3.6 shots/game at 10% accuracy — negligible
threat. We win the energy war decisively.

## Change this pass: gun lead weight W 0.25 -> 0.85
Replay-sim (per-tick interception over recorded enemy paths) run on TWO
independent 80-game slices + a 120-game power-tiered slice. CLEAN MONOTONIC rise
toward head-on for this SLOW target:
  W=0.0 ~33%, W=0.25 ~34%, W=0.5 ~37%, W=0.75 ~42%, W=1.0 ~50% hit.
With real power tiers + gunheat: W=0.25 = 35.1% hit / 90k dmg -> W=0.85 = 47.9% /
123k dmg (+36% damage). Physics: a slow, barely-moving target is best hit near
head-on (any lead overshoots). Hedged just short of 1.0 (W=0.85) because the
replay path was reactive to our OLD W=0.25 shots (biased).
Power tiers UNCHANGED (3.0/<350, 2.5/<550, 2.0/else + low-E clamps) — keeps far
shots net-positive, guarding the crazy-bot regression (never flat power 3.0 vs a
FAST dodger; THIS opponent is slow so it's safe).
Backup of prior source: /tmp/MyTank.bak.java (also git). Compiles Java 8 (major 52).

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
If ite_terminator's speed/turn rate RISES (becomes a fast dodger), LOWER W back
toward 0.25-0.5 (head-on misses fast movers -> that regressed us to 83% vs crazy).
Re-run the W-sweep replay on >=2 slices before changing. Keep MyTank class name +
Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass) — opponent = it_economics__ite_terminator

## STATUS: THE ROUND-1 W=0.85 GUN CHANGE WORKED — 100% WIN, NO CODE CHANGE
Verified /logs/rounds/{0,1} (this match, opponent it_economics__ite_terminator):
- Round 0 (W=0.25 gun): opus 44105 vs terminator 801. 10/10 firsts.
- Round 1 (W=0.85 half-lead, tuned for this SLOW target): opus 44187 vs
  terminator 738. results_0.txt: opus_4_8.MyTank 1762 (98%), 10/10 firsts.
  Enemy score DROPPED 801->738 and killtick DROPPED 306->297 (faster kills).

## Worst-game check on round 1: 0 LOSSES / 250
Analyzed all 250 sim_*.jsonl: losses=0. Enemy DIES every game (finalE 0.0).
Our worst final energy = 71.7 (sim_164); comfortable margin, no close games.
Mean killtick 296.9 (min 164, max 569). Theoretical-max score share.

## Opponent = SLOW, lightly-curving mover (unchanged profile)
avg |v| ~2.56, turn ~0.026 rad/tick, moving ~51% of ticks, fires ~3.6 low-acc
shots/game (negligible). We win the energy war decisively. W=0.85 (near head-on)
is data-optimal for a slow, barely-moving target (any bigger lead overshoots).

## Decision this pass: NO code change (deliberate)
Source is IDENTICAL to the round-1 winning commit (fa358dc) — verified via
`diff` of git show fa358dc:robots/custom/MyTank.java vs working copy = identical.
W=0.85 confirmed at line 183. Any gun/movement edit only risks regression on a
250/250 sweep we win with 71+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). If terminator's speed/turn rate RISES
(becomes a fast dodger), LOWER W back toward 0.25-0.5 (head-on misses fast movers
-> that regressed us to 83% vs robo_code__crazy). Re-run the W-sweep replay on
>=2 slices before changing. Never go flat power 3.0 vs a FAST dodger.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = tibola__markiv (FIRST FOE WITH 13 LOSSES)

## KEY FINDING: markiv is a COMPETENT MUTUAL-DAMAGE FIGHTER we were LOSING 13/250 to
Round 0 result (BEFORE my change): opus-4-8 41637 vs tibola__markiv 3404.
results_0.txt: opus 1736 (95%), 10/10 firsts BUT trace.md shows WIN RATE 95%
(237/250) — we LOSE 13 games. Games are LONG (avg 634 turns), our accuracy only
25%, avg min energy only 53 (much tighter than the ~90+ of easy foes).
Opponent = moderate curving mover: moving 43% of ticks, avg |v| 2.0, turn
0.052 rad/tick. It FIRES BACK effectively (a real gun, not passive).

## WHY WE LOST: enemy out-trades us at CLOSE range
Analyzed damage dealt each side in losses vs wins:
- LOSSES: enemy deals 109-140 dmg to us, we deal only 41-98 to it.
- WINS: we deal ~100+, enemy deals 64-111.
Enemy-hits-on-us BY DISTANCE (100 sims): 200px=689, 300px=1914, 400px=330,
500px=124, 600px=15. => Its gun is DEADLY at 200-400px and near-useless >450px.
Our OLD movement orbited at ~280px — right in its kill zone.

## CHANGES THIS PASS (movement is the real lever; gun tuned too)
1. MOVEMENT: orbit FURTHER OUT (~450px). rangeBias now pull-in >500, push-out
   <400 (was pull-in >350 to ~280px). At 450px the enemy's accuracy collapses
   while our own hit rate stays ~40% (replay-sim). Should slash the 13 losses.
2. DODGE: reverse-on-enemy-fire 50%->60%, rate-limit 6->5 ticks (bullets now
   travel further so reactive dodging is more effective). Still randomized.
3. GUN W: 0.85 -> 0.75. Replay-sim W-sweep (120 games, per-tick interception over
   recorded enemy paths) peaks at W=0.75 (45.0% hit) for this moderate curver.
4. POWER: widened 3.0 tier to <480px (was <350) to cover the new orbit distance.
   At ~40% hit rate every power is net-energy-positive; power 3.0 = most dmg/shot.

## Replay tool: /tmp/replay.py (rebuild from this note if lost)
Loads /logs/rounds/0/sim_*.jsonl (per-file header maps index->name; enemy=non-
'opus'). load(fn) -> {t:(enemyDict,ourDict)}. sweep(W,power) fires a bullet from
OUR recorded (x,y) with W-blend lead, steps at 20-3*power, hit if <18px to enemy's
recorded FUTURE pos, 800x600 bounds + gunheat. sweep_dist() = hit rate per range.
CAVEAT: biased — enemy path was reactive to our OLD close-orbit shots. The
enemy-hits-by-distance analysis (the movement lever) is the more trustworthy signal.

## Compile verified: javac --release 8 ... -> major version 52. Backup: /tmp/MyTank.bak.java (also git).

## For next teammate — IMPORTANT
- If NEW /logs win rate is STILL <100% or DROPPED below 95%, the farther orbit may
  have hurt (e.g. we lose accuracy or hug walls). First REVERT movement to
  /tmp/MyTank.bak.java (git prior: orbit ~280px, W=0.85) which won 95%, then retune.
- If win rate rose toward 100%: keep pushing orbit distance / dodge if margin allows.
- The core insight for THIS foe: it wins the CLOSE-range trade -> keep distance.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = tibola__markiv

## STATUS: round 1 improved 95%->97% (242/250); still LOSE 8 long grinds
Verified /logs/rounds/{0,1}: opus 41637(95%)/42293(93% share, 97% winrate) vs
markiv 3404/2936. The round-1 farther-orbit (~450px) change helped (fewer close
losses). Remaining 8 losses (sim_149/172/247/29/35/59/78/85) are ALL LONG games
(800-1688 turns) — energy-war grinds where WE bleed out first.

## ROOT CAUSE of the 8 losses: net-negative firing at range
markiv is an ENERGY-CONSERVING fighter: it fires only ~14 shots/game vs our ~30.
Real hit rate by distance (replay-sim, 120 games, W=0.75):
  <300px 52% | 300-450px 29% | 450-550px 31% | >550px 18.5%.
Net energy/shot = hitrate*3*power - power (POSITIVE iff hitrate > 1/3). At our
~450-550px engagement (~30% hit) every power tier is slightly NET-NEGATIVE, so
firing power-3 constantly drains us faster than the enemy. In losses we actually
LAND MORE hits than the enemy (our energy-gain 37-50 vs enemy 9-27) but we FIRE
2x more often, so our per-miss cost accumulates and we die first (~0 vs 12-78 E).
In WINS our hit-gain is higher (54-61) — the difference is hit rate variance.

## CHANGE THIS PASS: taper power by distance + tighter far-range fire gate
1. Power tiers: <300->3.0 (52% hit, net +1.68), 300-450->2.2, 450-550->2.0,
   >550->1.4 (18.5% hit -> tiny per-miss drain). Was 3.0/<480 flat.
2. Fire gate: skip far shots (dist>500) unless getEnergy()>enemyEnergy+3; tighter
   gun-align threshold 0.08 for dist>450 (was 0.12) so only high-confidence far
   shots fire. Close-range gate unchanged (0.12).
Grind-sim over the 8 loss games: our net firing energy improved -788 -> -649
(~18% less bleed) — should let us outlast the enemy in the grinds and convert
some losses. Win-game damage drops slightly (still easily kills the 100-HP enemy).

## Compile: javac --release 8 ... -> major version 52 (Java 8). Backup: /tmp/MyTank.bak.java

## For next teammate
- If NEW /logs win rate <97% or losses rise, REVERT to /tmp/MyTank.bak.java (git
  prior, 97%) — the taper may have made games too long. If win rate rose toward
  100%, the far power could go even lower (1.0) or fire-gate tighter.
- The remaining lever is MOVEMENT (dodge better to take fewer enemy hits in
  grinds) or WAVE SURFING (high risk; local harness broken, trust /logs only).
- Replay-sim (hit rate by distance / net-energy grind sim) reusable: load
  sim_*.jsonl (header maps idx->name, enemy=non-'opus'), fire W=0.75 lead bullet
  from our recorded (x,y), step at 20-3*power, hit if <18px to enemy future pos.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current) -- opponent = robo_code__regullarmonk

## KEY FINDING: opponent is a LINEAR OSCILLATOR (very exploitable)
Analyzed /logs/rounds/0/sim_*.jsonl (250 games, REAL opponent now present):
- Moves back-and-forth along a FIXED heading (bh constant ~4.34, NEVER turns body).
- Pauses at each endpoint: ~54% of ticks STATIONARY (|v|<0.5). Max speed 8.
- Its GUN barely tracks us: gun offset from head-on-to-us is ~pi/2 constant, so
  its aim is poor -> we win the DAMAGE battle. But prior versions LOST the ENERGY
  war (our finalE ~7 vs enemy ~26) by orbiting far (~485px) with low hit rate.

## Replay-sim (tools/replay_hitrate.py) hit-rate results, realistic fire timing:
- HEAD-ON aim (W=1.0): 12.9% overall = DOUBLE the full-lead (6.8%). Lead
  overshoots the pauses/reversals. Head-on wins because target is stationary ~half
  the time and oscillates around a center.
- Hit rate is dominated by DISTANCE (head-on): 100-200px=66%, 200-300px=29%,
  300-400px=19%, 400-500px=9%. We were spending only 3% of ticks <300px.

## Changes this round
1. Gun W = 1.0 (pure HEAD-ON) -- was 0.75. Best vs this oscillator.
2. Power tiers rescaled for closer combat: 3.0<250px, 2.5<400, 2.0<500, 1.5 else.
3. Movement rangeBias: orbit CLOSER (~330px) instead of ~485px. Pull in when
   dist>380, push out when dist<280. This raises our head-on hit rate massively.
Rationale: enemy gun is inaccurate so closing is low-risk; closing 2-3x's our hit rate.
Compiles to Java 8 (major version 52), rc=0.

## Tools
- tools/replay_hitrate.py : per-tick head-on/lead/hybrid hit-rate & by-distance
  breakdown from sim logs. Edit `files` glob for the round dir.

## Verify next round
Check new /logs/rounds/0/results_*.txt: want higher Bullet Dmg AND higher finalE
(energy war). If enemy suddenly moves differently, re-run tools/replay_hitrate.py.
Backup of prior MyTank at /tmp (not persisted) -- git has history if needed.

# Agent Notes (Round 2 / current pass) — opponent = robo_code__regullarmonk

## STATUS: round 1 improved 27%->78% winrate; THIS pass targets the 55 losses
Verified /logs/rounds/{0,1}: opus 19531(54% share)/36066(84%) vs monk 14868/7048.
Round-1 teammate's head-on gun + closer orbit (~330px) + rescaled power flipped
winrate 27%->78% (195/250 wins). This is a genuinely COMPETITIVE opponent (a
linear oscillator with a real gun) — our first non-trivial matchup this game.

## ROOT CAUSE of the remaining 55 losses: the 300-400px LOSE-LOSE zone
Analyzed round-1's 250 sim logs (per-file header maps idx->name, enemy=non-'opus'):
- We orbited ~330px -> spent 41629/67956 ticks (61%) in the 300-400px bucket.
- OUR head-on hit rate by distance: <200px 66%, 200-300px 36%, 300-400px ONLY 17%,
  400-500px 23%. Net energy/shot @power3 = hr*9-3 (break-even hr=1/3): 300-400px
  is NET -1.48/shot. We fired ~2500 shots there = massive energy bleed.
- ENEMY hit DENSITY (hits per 1000 ticks we spend there): 100-200px 4.5, 200-300px
  5.4, 300-400px 6.8 (HIGHEST), 400-500px 4.1. The enemy is MOST effective exactly
  where we camped. In losses we fired 54-82 shots (vs enemy 32-48, it conserves)
  and bled to 0 while enemy kept 11-61 E. Pure energy-war loss.

## FIX THIS PASS: orbit MUCH CLOSER (~230px) + taper power + tighter fire gate
1. MOVEMENT rangeBias: pull in when dist>270, push out when dist<180 (was >380/<280
   -> ~330px). New target ~230px = 200-300px zone where OUR hit DOUBLES (17%->36%,
   net-positive) AND enemy density DROPS (6.8->5.4). Below 200px is even better
   (66% hit, 4.5 density).
2. POWER tiers by measured hit rate: <300px=3.0 (net-positive), 300-400px=1.6
   (cheap misses in the 17% danger zone), 400-500px=1.8, else 1.2. Was
   3.0/<250,2.5/<400,2.0/<500,1.5.
3. FIRE GATE: skip shots at dist>300 unless getEnergy()>=enemyEnergy (was >500).
   Up close (<300px) always fire — those shots gain energy.

## Validation (net-firing-energy model over recorded games)
Model using MEASURED per-bucket hit rates: OLD config net = -777/1k ticks
(bleeding — matches the 55 losses); NEW config (closer orbit shifted dists inward
~100px) = +473/1k ticks (gaining). Direction is overwhelming even discounting
model optimism. Enemy-hit-density-by-distance is the trustworthy signal (not
reactive to our aim) and clearly favors closing to 200-300px.

## Compile: javac --release 8 ... -> major version 52 (Java 8). Backup: /tmp/MyTank.bak.java

## For next teammate — VERIFY THIS WORKED
- Check NEW /logs winrate: want it ABOVE 78% (ideally 90%+) and our avg min-energy
  UP from 23. If it DROPPED, the close orbit may have exposed us to a better
  close-range enemy gun -> REVERT to /tmp/MyTank.bak.java (git prior, 78%) or push
  orbit target back to ~280px (rangeBias thresholds 320/230).
- If winrate rose but still <95%: try pushing orbit even closer (~200px:
  thresholds 240/160) since our hit rate is 66% at 100-200px, OR lower the
  300-400px power to 1.2 and tighten the fire gate to always-skip dist>350.
- Re-run the distance analyses (hit-rate-by-dist + enemy-hit-density-by-dist) on
  the NEW logs — one-liners are in the step history / tools/replay_hitrate.py.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = andrekorol__oppswantmedead

## STATUS: 100% WIN (250/250), 92% share — RAISED POWER FOR MORE DAMAGE/SHARE
Round 0 result: opus-4-8 45054 vs andrekorol__oppswantmedead 2493. results_0.txt:
opus_4_8.MyTank 1811 (92%), 10/10 firsts; enemy 149 (8%). ZERO losses, zero close
games (worst our final E = 25.0 while enemy DIES every game; enemy finalE 0.0).
Trace: our win 100%, accuracy 48%, avg speed 5.3, avg min E 81.

## Opponent = SLOW STRAIGHT-LINE MOVER
Per-sim analysis: moving 46% of ticks, avg |v| 2.1, avg |dh| = 0.0 (NEVER turns
its body — pure straight-line back/forth). Fires ~8 shots/game at 32% acc.
Loses the energy war to us decisively. Mean kill tick ~269, avg engagement 303px.

## Replay-sim (per-tick interception, all 250 games, per-file header idx->name)
- W-sweep (head-on best, MONOTONIC): W=0.0 34.5% -> W=0.5 39.2% -> W=1.0 50.0%.
  Kept W=1.0 (head-on) — a slow non-turning target is best hit at current pos.
- Head-on hit rate BY DISTANCE @p3.0: 0-200px 66-98%, 200-300 48%, 300-400 52%,
  400-500 46%, 500-600 37%. EVERY bucket is net-energy-POSITIVE (hr>1/3; even
  500-600px = +0.36 net/shot @ p3). The old regullarmonk power taper (1.6/1.8 at
  300-500px, assuming 17% hit) was FAR too conservative here.

## CHANGE THIS PASS: raised power tiers + loosened fire gate
1. Power: OLD 3.0/<300, 1.6/<400, 1.8/<500, 1.2/else -> NEW 3.0/<550, 2.0/<650,
   1.5/else. Full power out to 550px (all net-positive here).
2. Fire gate: skip-when-behind-on-energy threshold 300px -> 550px (only gate the
   truly long low-hit shots). Align threshold tighten point 350->400px.
3. Low-E safety clamps (30->2.0, 15->1.0, 6->0.4) UNCHANGED. W=1.0 UNCHANGED.
   Movement UNCHANGED (orbit ~230px; it was fine at 100% win).

## Validation (damage/net-energy replay-sim over all 250 recorded games)
   OLD power+gate: dmg 42182, net +8679
   NEW power+gate: dmg 48842 (+16%), net +9166 (still net-POSITIVE, higher)
More bullet damage = higher score share + faster kills, and net energy IMPROVED
so no energy-war risk. Clear win, no downside. Compiles Java 8 (major version 52).
Backup of prior source: /tmp/MyTank.bak.java (also git).

## For next teammate
Only act if a NEW /logs shows win rate <100% or our min-energy collapsing to a
LOSS. If oppswantmedead becomes a FAST dodger (avg |v| rises, moving frac up) or
starts curving (avg |dh|>0), LOWER power back toward distance tiers and re-check
the W-sweep (never flat power 3.0 at long range vs a fast dodger -> regressed us
to 83% vs robo_code__crazy; but THIS opponent is slow/straight so full power to
550px is safe and validated). Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 / current pass) — opponent = andrekorol__oppswantmedead

## STATUS: 249/250 win (round 1) vs 250/250 (round 0). Added energy-war safety.
Verified /logs/rounds/{0,1}: opus 45054(92%)/44846(95%) vs oppswantmedead 2493/2323.
- Round 0 (conservative gun): 250/250 wins, 92% share.
- Round 1 (prior teammate raised power to flat 3.0 out to 550px): 249/250,
  95% share. The single LOSS = sim_96, an 865-turn energy-war GRIND at ~316px avg:
  we fired 46 shots to the enemy's 25 (it conserves energy) and DIED with enemy
  at 45 E. Aggregate: we're behind on energy only ~20% of ticks (we dominate the
  energy war), so the loss was rare variance where our real hit rate at 300-400px
  dipped below break-even in that game while we kept firing full power.

## CHANGE THIS PASS: rare energy-war power taper (defensive hedge)
Added AFTER the existing low-E safety clamps in aimAndFire:
    if (getEnergy() < enemyEnergy - 15 && dist > 350) power = min(power, 1.6);
Only triggers when we're BEHIND by >15 E AND at >350px (the lower-hit-rate zone).
Measured over 100 round-1 games: affects only 1.26% of shots -> does NOT touch
the dominant winning case (full power 3.0 stays for close range and when we lead),
but caps per-miss bleed in the rare grind so a bad streak can't drain us below a
conserving enemy. Kept W=1.0 head-on (opponent never turns body, avg |dh|=0),
power 3.0/<550, 2.0/<650, 1.5/else, orbit ~230px, all UNCHANGED.
Compiles Java 8 (major version 52). Backup of prior source: /tmp/MyTank.bak.java.

## For next teammate
- If NEW /logs shows win rate <100% or the taper hurt score share, first check:
  did we lose any grind games? If losses persist, LOWER the far power more (1.2)
  and widen the taper (dist>300). If share dropped with no new losses, the taper
  is too aggressive -> raise the threshold to enemyEnergy-25 or revert to
  /tmp/MyTank.bak.java (git prior, 95% share / 249 wins).
- Opponent = SLOW STRAIGHT-LINE MOVER (avg |v| 2.1, moving 46% ticks, never turns
  body). Head-on gun is optimal. It CONSERVES energy (fires ~8/game) -> it wins
  ONLY via long grinds, so the energy-war taper is the right lever.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = robo_code__fire

## STATUS: PERFECT WIN (250/250), 97% share — NO CODE CHANGE
Verified /logs/rounds/0:
- results.json: opus-4-8 44890 vs robo_code__fire 1252.
- results_0.txt: opus_4_8.MyTank 1799 (97%), 10/10 firsts; enemy 64 (3%).
- trace.md: our win 100% (250/250), accuracy 40%, avg speed 5.4, avg min E 90.
  Enemy: 0% win, 5.1 shots/game, 26% acc, speed 0.6, dies avg turn 298.

## Opponent = NEAR-STATIONARY (a "fire"-focused sitting-duck-ish bot)
Per-sim analysis (60 sims): moving only 14.1% of ticks, avg |v| 0.64, avg |dh|
0.007 (never turns body), avg engagement 310px. It fires back a bit (26% acc,
~5 shots/game) which is why ~3% leaks to it, but it's essentially stationary.
LOSSES: 0/250. Worst-game our final E = 77 (enemy DIES every game). Huge margin.

## Decision: NO gameplay change (deliberate)
Current gun (W=1.0 head-on, power 3.0 out to 550px, orbit ~230px) is data-optimal
for a near-stationary target (head-on = best; any lead overshoots). We already
score essentially the max share; the 3% leak is enemy survival-bullet damage,
not fixable without wave surfing (high risk, local harness broken). Any edit only
risks regression on a 250/250 sweep we win with 77+ E to spare.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
Head-on W=1.0 is optimal for this near-stationary foe. Keep MyTank class name +
Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass) — opponent = robo_code__fire

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (this match, opponent robo_code__fire):
- Round 0: opus 44890 vs fire 1252. results_0.txt: opus_4_8.MyTank 1799 (97%), 10/10 firsts.
- Round 1: opus 44xxx vs fire ~40. results_0.txt: 1782 (98%), 10/10 firsts.
- Full 250-sim sweep (round 1): LOSSES = 0/250. Our final E min/mean = 72.0/104.7.
  Enemy final E max/mean = 3.0/0.0 (enemy DIES every game). Kill tick mean ~287.

## Opponent = NEAR-STATIONARY "fire"-focused bot (unchanged profile)
Moving ~14% of ticks, avg |v| 0.64, never turns body (avg |dh| 0.007), engages
~296-310px. It fires back a little (~26% acc, ~5 shots/game) -> the ~2-3% leak is
enemy survival-bullet damage during the ~287 ticks before we kill it. Not fixable
without wave surfing (high risk, local harness broken -> can't validate).

## Decision: NO code change (deliberate)
Current gun (W=1.0 head-on, power 3.0 out to 550px, orbit ~230px) is data-optimal
for a near-stationary target (head-on best; any lead overshoots). We score
essentially the theoretical max share. Any edit only risks regression on a
250/250 sweep we win with 72+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
Head-on W=1.0 is optimal for this near-stationary foe. Keep MyTank class name +
Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = philipmjohnson__dacruzer

## KEY FINDING: dacruzer is a FAST CONSTANT-VELOCITY CURVING MOVER — head-on was WRONG
Round 0 result: opus-4-8 44343 vs philipmjohnson__dacruzer 1211. results_0.txt:
opus_4_8.MyTank 1771 (98%), 10/10 firsts. WON 250/250 BUT games were LONG
(avg 773 turns, max 1365) and our accuracy only 22% with tight worst-game energy
(worst final E 11.9-17.4; mean 55.7 — much tighter than easy foes at ~100+).

## Opponent profile (per-sim analysis, 250 games)
- moving 55% of ticks, avg |v| 4.12, avg |dh| 0.069 rad/tick (noticeable CURVE),
  engage dist ~375px, 5.6 walls/game. A genuine mobile mover, not a duck.

## Replay-sim W-sweep (per-tick interception over recorded paths, 2 independent slices)
  slice A (files[:80]):   headon 26.9%  W0.75 30.9  W0.5 33.8  W0.25 37.9  W0.1 44.9  LINEAR(W0.0) 46.1  circ 46.1
  slice B (files[120:200]): headon 24.8%  W0.75 28.0  W0.5 30.5  W0.25 35.7  LINEAR 44.5  circ 44.4
CLEAN, ROBUST: FULL LINEAR LEAD (W=0.0) ~45% vs head-on ~25% — nearly DOUBLE.
The prior config used W=1.0 (head-on) = nearly the WORST choice here! circ ≈ linear
(curve too mild to matter, so kept simple linear predictor).
Hit rate by distance (full lead): 0-100 69%,100-200 68%,200-300 56%,300-400 50%,
400-500 40%,500-600 29%,600-700 21%. All >1/3 break-even out to 500px.

## Damage/net-energy replay (all 250 games, distance-tiered power)
  W=1.0 (OLD): dmg 34895, net energy -4910 (we were BLEEDING in the long grinds!)
  W=0.0 (NEW): dmg 60036 (+72%), net energy +9287 (we GAIN energy)
This explains the long games + tight energy: head-on missed a curving mover so we
bled. Full lead flips it -> faster kills, bigger margin.

## CHANGES THIS PASS (robots/custom/MyTank.java)
1. Gun W: 1.0 -> 0.0 (full linear lead). THE key fix. (line ~230)
2. Power tiers retuned to measured hit-by-distance: 3.0/<500, 2.4/<620, 1.5/else
   (was 3.0/<550, 2.0/<650, 1.5). 500-600px is only 29% hit -> lower power there.
Movement UNCHANGED. Low-E safety clamps + energy-war taper UNCHANGED.
Compiles Java 8 (major version 52). Backup of prior source: /tmp/MyTank.bak.java.

## Replay tool: /tmp/rep.py (W-sweep), /tmp/dist.py (hit-by-distance), /tmp/dmg.py
Rebuild from these if lost. Load sim_*.jsonl (header maps idx->name, enemy=non-
'opus'), fire W-blend lead bullet from OUR recorded (x,y), step at 20-3*power,
hit if <18px to enemy future pos, 800x600 bounds + gunheat cooldown.

## For next teammate
- VERIFY new /logs: want games SHORTER, accuracy UP (~40%+), worst-game energy UP.
  If dacruzer becomes a REACTIVE stop-and-go dodger (avg |dh| up, moving frac
  down, stops when we fire), full lead will overshoot -> raise W toward 0.5-1.0;
  re-run /tmp/rep.py W-sweep on >=2 slices. If it stays a smooth mover, keep W=0.0.
- Never go flat power 3.0 at long range vs a FAST dodger (regressed us to 83% vs
  robo_code__crazy) — the distance taper guards this.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass) — opponent = philipmjohnson__dacruzer

## STATUS: THE ROUND-1 W=0.0 GUN CHANGE WAS A BIG WIN — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (this match, opponent philipmjohnson__dacruzer):
- Round 0 (OLD head-on W=1.0 gun): opus 44343 vs dacruzer 1211. 250/250 wins BUT
  long grinds (avg 773 turns), accuracy only 22%, worst-game final E ~12-17.
- Round 1 (prior teammate switched to W=0.0 FULL LINEAR LEAD + power tiers
  3.0/<500, 2.4/<620, 1.5/else): opus 44792 vs dacruzer 581. results_0.txt:
  opus_4_8.MyTank 1786 (99%), 10/10 firsts. HUGE improvement:
    * win rate 100% (250/250), 0 LOSSES
    * accuracy 22% -> 61%
    * game length avg 773 -> 361 turns (kills 2x faster)
    * worst-game final energy ~12 -> 92.5 (enemy DIES every game, finalE 0.0)
    * enemy score 1211 -> 581 (halved)
  The W=0.0 change flipped a tight grind into a crushing dominant win — dacruzer
  is a fast constant-velocity curving mover (avg|v|4.1, avg|dh|0.069) so full
  linear lead nearly DOUBLES hit rate vs head-on. Confirmed data-optimal.

## Worst-game check (round 1): 0 losses, worst final E 92.5
python one-liner (per-file header maps idx->name, enemy=non-'opus') confirmed
losses=0/250; worst-6 our final E: 92.5/98.4/102.6/107.2/107.4/107.5, enemy 0.0
in all. No close games. This is near-theoretical-maximum performance.

## Decision this pass: NO code change (deliberate)
Source is the round-1 winning config (W=0.0 full linear lead, power tiers
3.0/<500, 2.4/<620, 1.5/else, low-E safety clamps, energy-war taper, orbit ~230px).
Verified W=0.0 at line 230. Any gun/movement edit only risks regression on a
250/250 sweep we win with 92+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). dacruzer is a smooth fast mover -> KEEP W=0.0.
If it becomes a REACTIVE stop-and-go dodger (avg|dh| up, moving frac down, stops
when we fire), full lead will overshoot -> raise W toward 0.5-1.0 and re-run the
W-sweep replay (/tmp/rep.py per prior notes) on >=2 slices first. Never go flat
power 3.0 at long range vs a FAST dodger (regressed us to 83% vs robo_code__crazy;
the distance taper guards this). Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 5 / current — NEW OPPONENT: alpian__ianstank)

## KEY FINDING: opponent changed to a MOBILE stop-and-reverse OSCILLATOR
Prior rounds' opponent (wouterjoosse infinitylock) was stationary. The CURRENT
opponent in /logs/rounds/0 is `alpian__ianstank`: a mobile bot that oscillates
back-and-forth between two points along a line, STOPPING ~50% of ticks and
turning hard (~7.9 deg/tick avg) at reversals. It fires only ~10 shots/game
(energy-conserving). Max |v|=8.

## Round-0 result under the OLD code (W=0.0 full lead): won 234/250 sims, 86% score.
16 losses were all low-hit-rate games where our full-linear-lead OVERSHOT the
oscillator's pauses/reversals.

## CHANGE THIS ROUND (big win): switched aim to HEAD-ON (W=1.0)
Replay-sim over 80 recorded games (per-tick interception on enemy's ACTUAL future
path), hit rate by aim blend W (W=1.0=head-on, W=0.0=full linear lead):
   W=0.0 21.4%, W=0.25 23.2%, W=0.5 25.9%, W=0.75 32.8%, W=1.0 40.3%.
Monotonic — head-on is clearly best (a stop/reverse target defeats any lead).
In the 16 LOSS games specifically: W=0.0 hits 5.5% vs W=1.0 15.2% (~3x). This
directly attacks the loss cause.
Also: head-on hit rate by distance is 34-57% at EVERY bucket -> all net-energy-
positive. We already win the energy war 73 vs 2.2 avg final E. So power kept flat
3.0 out to 550px (was 500), 2.4 to 650, 1.5 beyond.

## Replay-sim tool: /tmp/replay.py (W-sweep) and /tmp/replay2.py (dist buckets).
Copy them into /workspace/tools/ if you want them persisted. They read
/logs/rounds/0/sim_*.jsonl, reconstruct enemy path, and test aim W hit rate.

## Recommendation for next teammate
Head-on aim should convert most of the 16 losses. If opponent unchanged, verify
win margin improved in new logs. If opponent changes again, re-run the W-sweep
replay to pick aim. Keep MyTank.java compiling to Java 8 (major version 52).

# Agent Notes (Round 2 / current pass) — opponent = alpian__ianstank

## STATUS: round 1 head-on gun won 249/250 (93% share). THIS pass fixes the grind bleed.
Verified /logs/rounds/{0,1}: opus 43302(86%)/45251(93%) vs ianstank 4939/3516.
Round-1 teammate's head-on (W=1.0) change was correct (86%->93%, 9->10 firsts).
Full 250-sim sweep round 1: 249 wins, 1 LOSS (sim_37). Our final-E mean 85, but
the 1 loss + ~8 CLOSE games (finalE 0-20) are ALL LONG GRINDS (861-1178 turns).

## ROOT CAUSE of loss/close games: energy-war bleed at range
ianstank = energy-CONSERVING stop-and-reverse oscillator: fires ~half as often as
us (~18-25 shots/game vs our 44-60). MEASURED real head-on hit rate by distance
(energy-gain events / fire events, 150 games):
  100-200px 70% | 200-300px 36% | 300-400px 21% | 400-500px 16% | 500-600px 17%.
Net energy/shot @p3 = hr*9-3: 300-400px = -1.11, 400-500px = -1.56 (BLEED!).
56% of ticks are 200-300px (marginal +0.24), 30% are 300-500px (net-negative).
In the LOSS (sim_37) we were BEHIND on energy 94% of ticks (vs ~26% in wins),
fired ~60 shots at 15% real hit, and drained to 0 while enemy kept energy.
Round-1 config fired flat power 3.0 out to 550px -> big per-miss drain in grinds.

## CHANGE THIS PASS: distance power taper + earlier energy-war cut
1. Power tiers: was flat 3.0/<550, 2.4/<650, 1.5/else. NOW 3.0/<300 (keeps the
   dominant 200-300px zone at full power, 36% hit = net-positive), 2.4/<400,
   1.6/<550, 1.0/else. Cuts per-miss cost where hit rate is below break-even.
2. Energy-war cut: was `ourE<enemyE-15 && dist>350 -> power<=1.6`. NOW
   `ourE<enemyE && dist>300 -> power<=1.0`. Triggers in the grind-loss state
   (behind on energy at range) and cuts power hard so misses barely cost energy.
Kept W=1.0 head-on (data-optimal for this stop-and-reverse oscillator), movement
unchanged (orbit ~230px, proven at 249/250).

## Validation (grind-sim over recorded games with MEASURED real hit rates)
  current (flat p3): all-150-games net firing energy -106, grind games -111.
  NEW (taperC):      all-150-games net +574, grind games -45 (60% less bleed),
  while retaining ~93% of winning-game bullet damage (22655 vs 24233) so score
  share barely drops. Should convert the loss + most close games to comfortable
  wins without sacrificing the dominant win margin.

## Tools: /tmp/grindsim2.py, /tmp/gs3.py (rebuild from these notes if lost).
Key one-liner (REAL hit rate by distance = energy-gain events / fire events):
count our energy DROPS in (-3.1,-0.05) as fires, GAINS >0.1 as hits, bucket by
the distance at the PREVIOUS tick. This is more trustworthy than the replay sim
(which overstates hit rate ~49% vs real ~36% at 200-300px because the enemy path
was reactive to our actual shots).

## Compile: javac --release 8 ... -> major version 52 (Java 8). Backup: /tmp/MyTank.bak.java

## For next teammate
- VERIFY new /logs: want the 1 loss GONE, worst-game final-E UP from 0-6, win rate
  100%, score share held >=93%. If share DROPPED with no fewer losses, the taper
  is too aggressive -> raise the 300px tiers back toward 3.0/<400. If losses
  PERSIST, cut far power more (400px->1.0) and orbit closer (rangeBias 250/160).
- ianstank fires only when we're close/predictable; the real remaining lever is
  MOVEMENT (fewer enemy hits in grinds) — high risk, local harness broken.
- Keep MyTank class name + Java-8 bytecode (only hard requirement). W=1.0 head-on.

# Agent Notes (Round 1 / current pass) — opponent = andrekorol__myfirstkiller

## STATUS: 100% WIN (250/250), 97% share — RAISED POWER TIERS FOR MORE DAMAGE
Round 0 result: opus-4-8 44967 vs andrekorol__myfirstkiller 2066. results_0.txt:
opus_4_8.MyTank 1793 (97%), 10/10 firsts; enemy 56 (3%). ZERO losses, zero close
games (worst our final E = 41.8 while enemy DIES every game; mean final E 110.6,
mean kill tick 275). Trace: our win 100%, accuracy 49%, avg speed 5.4.

## Opponent = SLOW STRAIGHT-LINE MOVER (never turns body)
Per-sim analysis (250 games, header maps idx->name, enemy=non-'opus'):
- moving 43% of ticks, avg |v| 1.94, avg |dh| = 0.0 (NEVER turns body — pure
  straight-line back/forth). Fires ~7 shots/game at 31% acc. Slow (avg speed 1.9).
  Loses the energy war to us decisively.

## Replay-sim (per-tick interception, 100 games, per-file header idx->name)
- W-sweep (head-on best, MONOTONIC): W=0.0 46.4% -> W=0.5 51.6% -> W=1.0 62.2%.
  Kept W=1.0 (head-on) — a slow straight-line target that's stationary ~half the
  time is best hit at current pos; any lead overshoots. Confirmed data-optimal.
- Head-on hit rate BY DISTANCE @p3.0: 0-100 79%, 100-200 71%, 200-300 65%,
  300-400 66%, 400-500 48%, 500-600 38%, 600-700 33%. EVERY bucket is net-energy-
  POSITIVE (hr>1/3). The old power taper (1.6/1.0 at 550px+) was too conservative.

## CHANGE THIS PASS: raised power tiers (validated +9% dmg AND higher net energy)
Power: OLD 3.0/<300, 2.4/<400, 1.6/<550, 1.0/else -> NEW 3.0/<400, 2.5/<550,
1.8/<650, 1.2/else. Replay-sim (120 games, distance-tiered): dmg 225742 -> 246123
(+9%), net energy 62279 -> 65226 (both up -> no energy-war risk). Full power out
to 400px (all net-positive here). SAFE because target is SLOW (not a fast dodger
-> no crazy-bot flat-power-3 regression). W=1.0 head-on UNCHANGED. Movement
UNCHANGED (orbit ~230px, proven at 250/250). Energy-war taper + low-E safety
clamps UNCHANGED (still guard the rare grind). Backup: /tmp/MyTank.bak.java (git).
Compiles Java 8 (major version 52). rc=0.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our min-energy collapsing to a
LOSS. If myfirstkiller becomes a FAST dodger (avg |v| rises, moving frac up) or
starts curving (avg |dh|>0), LOWER power back toward the old distance tiers and
re-check the W-sweep (never flat power 3.0 at long range vs a fast dodger ->
regressed us to 83% vs robo_code__crazy; THIS opponent is slow/straight so full
power to 400px is safe and validated). Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 / current pass) — opponent = andrekorol__myfirstkiller

## KEY FINDING: R1's raised power tiers REGRESSED the real game — REVERTED
Compared REAL game results (not biased replay-sim):
- Round 0 (game) config: power 3.0/<300, 2.4/<400, 1.6/<550, 1.0/else.
  Result: opus 44967 vs 2066, 97% share, killtick 275, enemy bullet dmg 56.
- Round 1 (game) config: R1 teammate RAISED to 3.0/<400, 2.5/<550, 1.8/<650,
  1.2/else. Result: opus 45002 vs 2250, 94% share, killtick 290 (SLOWER),
  enemy bullet dmg 108 (DOUBLED). WORSE on every metric.
WHY: higher power = longer gun cooldown (1+p/5). Power 3.0 -> 1.6s cd vs power
1.6 -> 1.32s. Firing power-3 at 350-400px means FEWER total shots + LONGER
engagement -> the enemy (a slow straight-line mover that fires back ~31% acc)
lands MORE hits on us over the extended fight. Faster kills = less exposure.

## CHANGE THIS PASS: reverted power tiers to the R0 (better) config
Restored 3.0/<300, 2.4/<400, 1.6/<550, 1.0/else. W=1.0 head-on UNCHANGED
(monotonically best for this ~half-time-stationary straight-line mover, 62% hit
per replay-sim). Movement, energy-war taper, low-E clamps UNCHANGED.
Both rounds won 250/250 with 0 losses and worst final E 26+ (huge margin) — the
revert is about maximizing SCORE SHARE (faster kills, less enemy damage), not
avoiding losses.

## LESSON (reinforces prior crazy-bot note): distrust the biased replay-sim's
## damage numbers. My replay-sim over round-1 paths said R1's higher power gave
## marginally MORE damage (29305 vs 27218) — but the REAL game showed R1 was
## SLOWER and let the enemy score MORE. The enemy path in the sim was reactive to
## R1's actual (raised-power) shots, so it can't fairly compare cooldown effects.
## The REAL cross-round game metrics (killtick, enemy dmg, share) are decisive.

## Compile verified: javac --release 8 ... -> major version 52 (Java 8), exit 0.

## For next teammate
- If NEW /logs shows share < 97% or killtick > 275, do NOT raise power again.
  If share rose toward 97%+, the revert worked. Keep MyTank class name + Java-8.
- General principle: against a slow bot that fires back, FASTER kills (lower
  power at range = shorter cooldown = more shots landed early) beat MORE per-shot
  damage. Only raise power when the enemy is truly passive AND you already win at
  max speed.

# Agent Notes (Round 1 / current pass) — opponent = alpian__tarektank

## STATUS: 249/250 win (99.6%), 92% share — targeted the ONE grind loss
Round 0 result: opus-4-8 44954 vs alpian__tarektank 3577. results_0.txt:
opus_4_8.MyTank 1832 (92%), 10/10 firsts; enemy 160 (8%). Games are LONG
(avg 554, max 1371 turns), our accuracy 34%. ONE LOSS: sim_20, a 1223-turn
energy-war grind where we were behind on energy 97% of ticks and bled to 0
(enemy kept 26 E).

## Opponent = SLOW STRAIGHT-LINE MOVER that CONSERVES energy
Per-sim analysis (80 games, header maps idx->name, enemy=non-'opus'):
- avg |v| 2.56, moving 48% of ticks, avg |dh| = 0.0 (NEVER turns body — pure
  straight-line back/forth with pauses), engages ~293px. Fires ~11 shots/game
  at 33% acc (energy-conserving — fires ~half as often as our 25).

## Gun aim: W=1.0 head-on is CONFIRMED OPTIMAL (kept unchanged)
Replay-sim W-sweep (80 games, per-tick interception): MONOTONIC toward head-on
  W=0.0 35.8% | W=0.25 38.9% | W=0.5 42.6% | W=0.75 47.6% | W=1.0 53.9%.
A slow straight-line target that's stationary ~half the time is best hit at
current pos; any lead overshoots. W=1.0 unchanged (was already correct).

## Real hit rate by distance (energy-drop=fire, energy-gain=hit, 150 games)
  100-200px 68% | 200-300px 36% | 300-400px 25% | 400-500px 20% | 500-600px 22%.
Break-even = 33% (net = hr*3p - p). We spend 57% of ticks at 200-300px (net+),
19% at 300-400px (net-NEGATIVE bleed), 12% beyond 400px (bleed). This mid/far
net-negative firing is what drains us in the rare grind.

## CHANGE THIS PASS: strengthened energy-war handling (grind-loss fix only)
1. Energy-war taper: `getEnergy()<enemyEnergy && dist>300 -> power<=1.0` changed
   to `power<=0.8` (cheaper misses when behind).
2. NEW far-shot gate: `dist>400 && getEnergy()<enemyEnergy -> don't fire` (400px+
   hit rate ~20% = net-negative; conserve to outlast in the grind).
Both trigger ONLY when strictly behind on energy. Trigger frequency: 7.5% of
ticks in WINNING games vs 29.5% in the LOSS game — so it targets the grind state
without meaningfully slowing the 249 comfortable wins (where we're ahead on
energy). Grind-model over 150 games: net firing energy +20% (1009 -> 1206).
Used STRICT `<enemyEnergy` (not +10) after checking: +10 triggered 12.4% of
winning ticks (too much — risks the myfirstkiller slow-kill regression).
Power tiers (3.0/<300, 2.4/<400, 1.6/<550, 1.0/else), W=1.0, movement (orbit
~230px), low-E clamps ALL UNCHANGED. Backup of prior source: /tmp/MyTank.bak.java.
Compiles Java 8 (major version 52), rc=0.

## For next teammate
- VERIFY new /logs: want the sim_20-style grind loss GONE (win rate 100%) and
  score share held >=92%. If share DROPPED with no fewer losses, the taper/gate
  is too aggressive (slowing wins) -> revert the far-shot gate first
  (/tmp/MyTank.bak.java is the 249/250 prior). If losses PERSIST, orbit closer
  (rangeBias thresholds 250/160 -> ~200px where our hit rate is 68%).
- tarektank is SLOW/straight -> KEEP W=1.0 head-on. If it becomes a fast dodger
  (avg|v| up, moving frac up) or curves (avg|dh|>0), re-run the W-sweep replay.
  Never go flat power 3.0 at long range vs a fast dodger (regressed to 83% vs crazy).
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = alpian__tarektank

## STATUS: round 1 won 249/250 (90% share in results_0). 1 LOSS + several close
## games are ALL long grinds. THIS pass: orbit CLOSER (~230px -> ~180px).

## Verified /logs/rounds/{0,1}: opus 44954/45355 vs tarektank 3577/3995.
Round 1 trace: win rate 100% (249/250), the 1 loss = sim_34 (1077-turn grind,
we behind on energy 97% of ticks, bled to 0 vs enemy 13E). Close wins sim_7/215/
55 finished with 2-16 E — all long grinds too. Games are LONG (avg 552 turns).

## ROOT-CAUSE ANALYSIS (net energy by distance, 150 games — the decisive signal)
Measured OUR real hit rate (energy-gain/fire events) AND enemy hit density AND
computed NET energy/1k ticks per distance bucket:
  100-200px: our hit 70%, enemy 11.9/1k -> NET +46/1k  (ONLY net-POSITIVE zone!)
  200-300px: our hit 38%, enemy  9.6/1k -> NET -55/1k
  300-400px: our hit 21%, enemy  3.5/1k -> NET -73/1k
  400-500px: our hit 29%, enemy  3.2/1k -> NET -30/1k
We orbited ~230px -> 55% of ticks in the 200-300 LOSING zone. Even though the
enemy hits us slightly more often up close (11.9 vs 9.6/1k), our 70% hit rate
there DOMINATES. Orbiting closer flips the grind energy war in our favor.

## CHANGE THIS PASS (movement only): orbit ~230px -> ~180px
rangeBias thresholds: pull-in >270->>210, push-out <180-><140. Only functional
change (verified via diff: 2 lines). Power tiers UNCHANGED (3.0/<300 covers the
new close zone at full power where hit=70% -> net-positive). W=1.0 head-on
CONFIRMED optimal via fresh replay-sim W-sweep on round-1 logs (48.7% @ W=1.0 vs
30% @ W=0.0, monotonic — tarektank is a slow stop-and-reverse straight mover).
Movement reversals/dodge, fire gates, energy-war taper ALL UNCHANGED.
Compiles Java 8 (major version 52). Backup of prior source: /tmp/MyTank.bak.java.

## For next teammate — VERIFY
- Want the sim_34-style grind loss GONE (win rate 100%), close-game final-E UP
  from 0-16, and share held/raised >=90%. If share DROPPED or NEW losses appear,
  the closer orbit may have exposed us to more enemy close-range hits -> push
  orbit back toward ~200px (thresholds 230/160) or revert to /tmp/MyTank.bak.java
  (git prior, 249/250). If it worked, could try ~160px (thresholds 190/130) since
  100-200px is +46/1k net.
- tarektank is SLOW/straight -> KEEP W=1.0 head-on. Keep MyTank + Java-8 bytecode.
- Net-energy-by-distance one-liner (the decisive tool): bucket by dist at prev
  tick; count our energy drops in (-3.1,-0.05)=fire, gains>0.1=hit for OUR hit
  rate; our drops <-3.5=enemy hit for density; NET = fires*hr*3p - fires*p - eh*~8.

# Agent Notes (Round 1 / current pass) — opponent = it_economics__ite_cliffbot2

## STATUS: PERFECT WIN (250/250), 96% share — NO CODE CHANGE (data-optimal)
Verified /logs/rounds/0:
- results.json: opus-4-8 44717 vs it_economics__ite_cliffbot2 1323.
- results_0.txt: opus_4_8.MyTank 1819 (96%), 10/10 firsts; enemy 80 (4%).
- trace.md: our win 100% (250/250), accuracy 73%(!), avg speed 5.4, avg min E 94.
  Enemy: 0% win, 3.5 shots/game, 22% acc, avg speed 2.4, dies avg turn 176.

## Opponent = SLOW, LIGHTLY-CURVING mover
Per-sim analysis (60 games, header maps idx->name, enemy=non-'opus'):
- moving 53.5% of ticks, avg |v| 2.65, avg |dh| 0.029 rad/tick (mild curve).
  Loses the energy war to us decisively (fires ~3.5 shots/game). Games are SHORT
  (avg 327 turns) — we kill it fast.

## Verified 0 LOSSES / huge margin
All 250 sims: losses=0. Worst-game our final E = 97.0; mean 126.0. Enemy DIES
every game. This is essentially the theoretical maximum score share (the 4% leak
is unavoidable enemy survival-bullet damage during the ~176 ticks before we kill).

## Gun aim: W=1.0 head-on CONFIRMED data-optimal (replay-sim, 80 games)
W-sweep (per-tick interception over recorded paths, power 3.0):
  W=0.0 63.4% | W=0.25 66.6% | W=0.5 69.5% | W=0.75 74.3% | W=1.0 78.2%.
MONOTONIC toward head-on — a slow, only-lightly-curving target that's stationary
~half the time is best hit at current pos; any lead overshoots. Matches our real
73% accuracy. Current power tiers (3.0/<300, 2.4/<400, 1.6/<550, 1.0/else),
orbit ~180px, energy-war taper, low-E clamps ALL correct for this energy-loser.

## Decision: NO code change (deliberate)
We score essentially the max. Any gun/movement edit only risks regression on a
250/250 sweep we win with 97+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
cliffbot2 is SLOW/lightly-curving -> KEEP W=1.0 head-on. If it becomes a FAST
dodger (avg|v| up, moving frac up), LOWER W toward 0.5 and re-run the W-sweep.
Never go flat power 3.0 at long range vs a fast dodger (regressed to 83% vs crazy).
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass) — opponent = it_economics__ite_cliffbot2

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (this match, opponent it_economics__ite_cliffbot2):
- Round 0: opus 44717 vs cliffbot2 1323. results_0.txt: opus_4_8.MyTank 1819 (96%), 10/10 firsts.
- Round 1: opus 45121 vs cliffbot2 1950. results_0.txt: opus_4_8.MyTank 1796 (95%), 10/10 firsts.
- Full 250-sim sweep (round 1): LOSSES = 0/250. Enemy DIES every game (finalE 0.0).
  Our worst final energy = 90.6 (sim_83); huge margin, no close games.
  Mean kill tick ~178 (min 129, max 288) — we kill FAST.

## Opponent = SLOW, lightly-curving mover (unchanged profile from R0 notes)
moving ~53% of ticks, avg |v| 2.65, avg |dh| 0.029 (mild curve). Loses the energy
war decisively (fires ~3.5 shots/game). W=1.0 head-on data-optimal (replay-sim
W-sweep monotonic to head-on 78%; matches our real ~73% accuracy). The ~4-5% leak
is unavoidable enemy survival-bullet damage during the ~178 ticks before we kill.

## Decision: NO code change (deliberate)
We score essentially the theoretical max (survival + all bonuses maxed). Faster
kills wouldn't raise the 95-96% share meaningfully, and any gun/movement edit only
risks regression on a 250/250 sweep we win with 90+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). cliffbot2 is SLOW/lightly-curving -> KEEP
W=1.0 head-on. If it becomes a FAST dodger (avg|v| up, moving frac up), LOWER W
toward 0.5 and re-run the W-sweep. Never go flat power 3.0 at long range vs a fast
dodger (regressed to 83% vs robo_code__crazy). Keep MyTank class name + Java-8.

# Agent Notes (Round 1 / current pass) — opponent = team488__meow (STRONG CURVING DODGER)

## KEY FINDING: first genuinely tough foe in a while — FAST, HEAVILY-CURVING mover with a GOOD gun
Round 0 result (BEFORE my change): opus-4-8 39720 vs team488__meow 4779.
results_0.txt: opus 1684 (93%), 10/10 firsts BUT trace.md shows WIN RATE 93%
(232/250) — we LOSE 18 games. Games are LONG (avg 761, max 1729 turns). Our
accuracy only 20%; ENEMY accuracy 37% (its gun is BETTER than ours). Our avg
min energy only 42 (tight). Losses are ~500-700 turn games where the enemy
out-DAMAGES us and finishes with 2-44 energy (not just slow grinds).

## Opponent profile (per-sim, 80 games; header maps idx->name, enemy=non-'opus')
- moving 91% of ticks, avg |v| 5.4 (fast), avg |dh| 0.115 rad/tick (STRONG curve),
  engage dist ~284px. A real orbiting/curving dodger, NOT a duck.

## CHANGES THIS PASS (gun rewrite + orbit distance) — both replay-sim validated
1. GUN: replaced linear predictor with proper CIRCULAR TARGETING (step enemy
   forward each future tick applying a SMOOTHED turn rate; enemyTurnRate now EMA
   0.6/0.4). Set W=0.0 so we use the full circular lead. Replay-sim over 2 slices
   (tools/replay2.py): circular 20.8%/25.7% hit vs head-on 16.0%/16.0% vs
   linear 2.9%/4.4%. ~50% more hits — directly attacks our 20% accuracy problem.
2. ORBIT: was ~180px. With circular gun our hit rate is ~25% at BOTH 100-200px
   AND 200-300px, but ENEMY hit density is 14.8/1k at 100-200px vs only 3.3/1k
   at 200-300px. So orbit ~260px (rangeBias pull-in >290, push-out <220): SAME
   hit rate, ~4x FEWER enemy hits. Attacks the 18 losses (enemy out-trades close).
Power tiers (3.0/<300, 2.4/<400, 1.6/<550, 1.0/else), fire gates, energy-war
taper, low-E clamps ALL UNCHANGED. Backup: /tmp/MyTank.bak.java (also git).
Compiles Java 8 (major version 52), rc=0.

## Analysis tools (persisted this pass)
- tools/replay2.py: circular vs head-on vs linear hit rate + hit-rate-by-distance.
  Usage: python3 tools/replay2.py 60  (arg = #games). Edit files[120:200] for a
  2nd slice. bydist() at the bottom prints hit rate per 100px bucket.
- tools/replay_meow.py: earlier W-sweep + basic circular test.
- Key one-liner (net our energy & enemy hit density by distance) is in step
  history: bucket by dist at prev tick; our energy drops (-3.1,-0.05)=fire,
  gains>0.1=our hit, drops<-3.5=enemy hit us. 200-300px was our worst NET zone
  under the OLD 180px orbit (-136/1k) — the orbit-out fix targets exactly this.

## For next teammate — VERIFY
- Want NEW /logs win rate ABOVE 93% (ideally 97%+), our accuracy UP from 20%,
  our avg min-energy UP from 42, fewer/no losses. If it DROPPED, first suspect
  the circular gun (if enemy became a stop-and-go/reactive dodger, circular
  overshoots -> set W back toward 0.5-1.0) OR the wider orbit (if enemy's gun is
  ALSO good at mid-range, pull orbit back to ~200px: thresholds 230/160). Revert
  to /tmp/MyTank.bak.java (git prior, 93%) if a clear regression.
- Circular targeting degrades GRACEFULLY: turn rate ~0 or velocity ~0 -> head-on.
- The remaining lever if still losing is WAVE SURFING (enemy gun is 37% accurate
  — dodging its bullets is the biggest untapped win, but high-risk; local harness
  broken, trust /logs only). Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 verification pass) — opponent = team488__meow

## STATUS: THE ROUND-1 CIRCULAR-GUN CHANGE WORKED BIG — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (this match, opponent team488__meow, a FAST heavily-
curving dodger with a good gun):
- Round 0 (OLD linear-lead gun, orbit ~180px): opus 39720 vs meow 4779.
  win rate 93% (232/250) — we LOST 18 games. accuracy 20%, enemy accuracy 37%.
- Round 1 (prior teammate switched to CIRCULAR TARGETING W=0.0 + orbit-out ~260px):
  opus 42402 vs meow 1117. results_0.txt: opus_4_8.MyTank 1721 (98%), 10/10 firsts.
  HUGE improvement:
    * win rate 93% -> 100% (250/250), 0 LOSSES
    * our accuracy 20% -> 45%
    * enemy score 4779 -> 1117 (dropped 77%)
    * enemy accuracy dropped (fewer of its bullets land — orbit-out worked)
  Confirmed via full-250-sim sweep: losses=0, worst-game our final E = 34.0
  (sim_10, 810-turn grind) while enemy DIES every game (finalE 0.0). Mean far
  above that. The circular gun + wider orbit is data-and-real-result-optimal.

## Opponent = FAST heavily-curving dodger (CONSISTENT across rounds)
Round 0: movefrac 0.91, avg|v| 5.36, avg|dh| 0.104.
Round 1: movefrac 0.92, avg|v| 5.66, avg|dh| 0.067. Same bot, same profile.
Circular targeting (step enemy forward applying smoothed EMA turn rate, W=0.0)
matches this profile — that's why accuracy more than doubled.

## Replay-sim on ROUND-1 logs is BIASED — do NOT trust it to re-pick the gun
Ran a W-sweep on round-1 logs (35 games): headon 37.9% > circ 31.4% > W0.5 21.3%.
This is the KNOWN reactivity bias — the enemy's round-1 path was reactive to our
ACTUAL circular-gun shots, so the replay cannot fairly compare aims. The REAL
cross-round game result (93%->100%, accuracy 20%->45% with circular) is decisive
and overrides the replay. DO NOT switch back to head-on based on the replay.

## Decision this pass: NO code change (deliberate)
Source is IDENTICAL to the round-1 winning config (verified `git diff` of
robots/custom/MyTank.java = empty). Circular gun (W=0.0, enemyTurnRate EMA
0.6/0.4, 12-iter stepped predictor), orbit ~260px (rangeBias pull-in >290,
push-out <220), power tiers 3.0/<300 2.4/<400 1.6/<550 1.0/else, energy-war taper,
low-E clamps. Any edit only risks regression on a 250/250 sweep we win with 34+ E
to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). meow is a FAST curving dodger -> KEEP the
CIRCULAR gun (W=0.0). Do NOT switch to head-on off the biased replay-sim.
The only remaining lever if it ever regresses is WAVE SURFING (enemy gun ~37%
accurate; dodging its bullets is the biggest untapped win, but high-risk — local
harness broken, trust /logs only). Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 1 / current pass) — opponent = robo_code__corners

## STATUS: PERFECT WIN (250/250), 98% share — NO CODE CHANGE (theoretical max)
Verified /logs/rounds/0:
- results.json: opus-4-8 44354 vs robo_code__corners 947.
- results_0.txt: opus_4_8.MyTank 1770 (98%), 10/10 firsts; enemy 40 (2%).
- trace.md: our win 100% (250/250), accuracy 77%(!), avg speed 5.4, avg min E 95.
  Enemy: 0% win, 5.9 shots/game, 18% acc, avg speed 1.7, dies avg turn 240.

## Opponent = the "Corners" sample bot (drives to a corner, then near-stationary)
Per-sim analysis (60 games, header maps idx->name, enemy=non-'opus'):
- 79% of ticks STATIONARY, avg |v| only 1.54, avg |dh| 0.018 (minimal turning).
  It races to a corner then camps and sweeps its gun -> essentially a sitting
  duck once cornered. Loses the energy war decisively (fires ~6 low-acc shots).

## Verified 0 LOSSES / huge margin
All 250 sims: losses=0. Worst-game our final E = 114.0 (mean much higher). Enemy
DIES every game. This is the theoretical maximum score share (the 2% leak is
unavoidable enemy survival-bullet damage before we corner-kill it).

## Gun aim: W value is IRRELEVANT here (target near-stationary)
Replay-sim W-sweep (80 games, per-tick interception, per-file header idx->name):
  W=0.0 71.8% | W=0.25 71.1% | W=0.5 71.1% | W=0.75 71.1% | W=1.0 71.6%.
All ~71% because a near-stationary target has lead ~= current pos regardless of W.
Real-game accuracy is even higher (77%). Current W=0.0 (circular lead, degrades to
head-on at v~0) is already optimal-tier. No gun change possible/needed.

## Decision: NO code change (deliberate)
We score essentially the max. Any gun/movement edit only risks regression on a
250/250 sweep we win with 114+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
Corners is near-stationary once cornered -> W=0.0 (=head-on at v~0) is optimal.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass) — opponent = robo_code__corners

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (this match, opponent robo_code__corners, the
"Corners" sample bot — races to a corner then camps near-stationary):
- Round 0: opus 44354 vs corners 947. results_0.txt: 1770 (98%), 10/10 firsts.
- Round 1: opus 44565 vs corners 1237. results_0.txt: 1796 (97%), 10/10 firsts.
- Full 250-sim sweep (round 1): LOSSES = 0/250. Enemy DIES every game.
  Our worst final energy = 111.8 (sim_35) — enormous margin, no close games.

## Decision: NO code change (deliberate)
We score essentially the theoretical max share (the 2-3% leak is unavoidable
enemy survival-bullet damage before we corner-kill it). Corners is near-stationary
once cornered -> W=0.0 (circular lead degrades to head-on at v~0) is optimal
(replay-sim W-sweep flat ~71% all W; real accuracy 77%). Any gun/movement edit
only risks regression on a 250/250 sweep we win with 111+ E to spare.
Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
Corners is near-stationary once cornered -> KEEP W=0.0. Keep MyTank class name +
Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = it_economics__ite_florian2

## STATUS: 249/250 win (round 0), 95% share — TUNED GUN W 0.0 -> 1.0 (head-on)
Round 0 result: opus-4-8 42572 vs it_economics__ite_florian2 2423. results_0.txt:
opus_4_8.MyTank 1703 (95%), 10/10 firsts; enemy 96 (5%). The single non-win
(sim_151) was NOT a loss: we were WINNING (ourE 85.4 vs enemyE 6.0) but the round
hit the 409-turn limit before we finished the kill -> enemy survived. Faster kills
convert that to a full win + more score.

## Opponent = SLOW, NEAR-STRAIGHT-LINE mover (barely fires)
Per-sim analysis (250 games, header maps idx->name, enemy=non-'opus'):
- moving only 29% of ticks, avg |v| 1.61, avg |dh| 0.016 (near-straight),
  engage ~324px. Fires rarely: enemy hit density only ~1-2/1000 ticks at EVERY
  distance (near-harmless gun). Loses the energy war decisively.

## CHANGE 1: gun W 0.0 -> 1.0 (head-on). THE key fix.
The gun was left at W=0.0 (full CIRCULAR lead) from the prior team488__meow match
(a FAST curving dodger). That is WRONG for this slow near-straight mover. Replay-
sim W-sweep (per-tick interception over recorded paths, TWO independent 80-game
slices), MONOTONIC toward head-on:
  slice A: W0.0 48.9% W0.25 50.4 W0.5 52.5 W0.75 55.4 W1.0 58.0
  slice B: W0.0 50.0% W0.5 52.9 W0.75 55.8 W1.0 60.3
Head-on best by ~10 points (a slow near-stationary target -> any lead overshoots).

## CHANGE 2: orbit ~260px -> ~200px (rangeBias 290/220 -> 230/170)
Enemy barely fires (hit density ~1-2/1k at ALL distances; 200-300px 1.72 is
actually HIGHER than 100-200px 1.23). Our head-on hit rate by distance: 100-200px
86%, 200-300px 47%, 300-400px 33%. So orbiting closer is BOTH safer AND far more
accurate -> faster kills -> converts the turn-limit non-win + raises score share.

## Compile verified: javac --release 8 ... -> major version 52 (Java 8), rc=0.

## For next teammate
- VERIFY new /logs: want win rate 100% (the sim_151 turn-limit non-win GONE),
  killtick DOWN, score share UP from 95%. If share DROPPED or new losses appear,
  the closer orbit may have exposed us -> push orbit back (rangeBias 270/200) or
  revert W (unlikely). florian2 is SLOW/straight -> KEEP W=1.0 head-on.
- If opponent changes to a FAST curving dodger, set W=0.0 (circular) again and
  orbit back out ~260px (that beat team488__meow 100%). Re-run the W-sweep first:
  the replay tools are in tools/ (replay_wsweep.py etc). Never flat power 3.0 at
  long range vs a fast dodger. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 verification pass) — opponent = it_economics__ite_florian2

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (opponent it_economics__ite_florian2, a SLOW near-
straight-line mover that barely fires):
- Round 0 (before head-on tune): opus 42572 vs florian2 2423. results_0.txt:
  1703 (95%), 10/10 firsts.
- Round 1 (prior teammate: W=1.0 head-on + orbit ~200px): opus 43243 vs florian2
  2388. results_0.txt: 1759 (92%), 10/10 firsts.
- Full 250-sim sweep round 1: LOSSES = 0/250, close(<20E) = 0. Mean our final
  energy 105.7 (min 29.4). MEDIAN killtick 261 (was 308 in R0 — FASTER kills),
  mean engagement 293px (was 324). Enemy DIES every game.

## The R1 change IMPROVED every REAL metric (results_0.txt share is noise)
Cross-round 250-sim comparison: killtick 308->270, our final E 99.7->105.7,
engagement 324->293px. The results_0.txt "95%->92%" is a 10-round SAMPLE artifact
(enemy bullet dmg 96->154 in that tiny sample); the full 250-sim data shows R1's
head-on+closer-orbit is strictly better. Enemy deals only ~10 dmg/game total
(0.7 hits/game) so score share is essentially maxed regardless.

## Only 3/250 games run long (>500 turns, max 1042) — all at ~400px engagement
In these the enemy drifts/stays at range and our inward rangeBias (±0.5 rad,
pull in when dist>230) isn't strong enough to close vs its drift + our wall
smoothing. Considered a stronger pull but REJECTED: these 3 games are still WINS
with huge margins, enemy is near-harmless, and touching a 250/250 config for a
1.2% tail carries pure regression risk with negligible score upside.

## Decision: NO code change (deliberate)
Source is IDENTICAL to the round-1 winning commit 8ff8290 (verified `git diff`
empty). W=1.0 head-on (data-optimal for this slow near-straight mover: replay-sim
monotonic W=1.0 58-60% vs W=0.0 49-50%), orbit ~200px, power tiers 3.0/<300
2.4/<400 1.6/<550 1.0/else, energy-war taper, low-E clamps, fire gates. Re-verified
compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
florian2 is SLOW/near-straight & barely fires -> KEEP W=1.0 head-on. If it becomes
a FAST curving dodger (avg|v| up, moving frac up, avg|dh|>0), set W=0.0 (circular)
and orbit back out ~260px (that beat team488__meow 100%); re-run the W-sweep first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = robo_code__myfirstrobot

## STATUS: 99% win (248/250), 87% share — TIGHTENED ORBIT to fix 2 grind losses
Round 0 result: opus-4-8 45538 vs robo_code__myfirstrobot 4694. results_0.txt:
opus 1726 (87%), 9/10 firsts (enemy got 1 first!). trace.md: win rate 99%
(248/250), our accuracy only 29%, avg min E 63, games LONG (avg 605, max 2015).
2 LOSSES: sim_0 (423t) and sim_78 (507t) — energy-war grinds at ~380-407px avg
distance where we were behind on energy 81-98% of ticks and bled to 0.

## Opponent = SLOW near-straight-line mover (avg|v| 2.47, avg|dh| 0.0087, 51%
stationary). Fires ~12 low-power shots/game, avg only 4.2 dmg/hit (weak gun).
Head-on (W=1.0) is data-optimal — kept unchanged.

## ROOT CAUSE of the 2 losses: fighting at ~400px (net-negative energy zone)
Measured OUR hit rate + enemy hit density by distance (150 games):
  100-200px: hit 48%, enemy 10.6/1k, fire_net +60/1k, enemy_dmg 45/1k -> NET +15/1k
  200-300px: hit 32%, enemy  9.7/1k, fire_net  -6/1k, enemy_dmg 41/1k -> NET -47/1k
  300-400px: hit 18%, enemy  3.1/1k, fire_net -72/1k -> NET -85/1k
100-200px is the ONLY net-energy-POSITIVE zone (48% hit >> 33% break-even). The
2 losses got stuck at 400px (net -85/1k) and bled out. We orbited ~200px (mostly
200-300px = -47/1k) which barely wins the war -> variance losses when pushed out.

## CHANGE THIS PASS (movement only): orbit ~200px -> ~150px + calmer reversals
1. rangeBias: pull-in threshold 230->180 (stronger -0.6 rad), push-out 170->130.
   Target ~150px = the 48%-hit net-positive zone. Enemy gun is weak (4.2 dmg/hit)
   so closing is low-risk and DOUBLES our hit rate vs 300-400px.
2. Reversal on enemy-fire: 60%->45%, rate-limit 5->6 ticks. Fewer disruptive
   reversals so we actually CLOSE the distance (was bouncing out to 400px in the
   grind losses). Enemy fires rarely so heavy dodging wasn't buying much.
Gun (W=1.0 head-on), power tiers (3.0/<300 2.4/<400 1.6/<550 1.0/else), fire
gates, energy-war taper ALL UNCHANGED. Backup: /tmp/MyTank.bak.java (also git).
Compiles Java 8 (major version 52), rc=0.

## For next teammate — VERIFY
- Want the 2 grind losses GONE (win rate 100%), our avg min-E UP from 63, games
  SHORTER (avg <605), share UP from 87%. If a regression (new losses / share
  drop), the close orbit may expose us more -> push orbit back to ~180px
  (thresholds 210/150) or revert to /tmp/MyTank.bak.java (git prior, 99%).
- myfirstrobot is SLOW/near-straight & weak-gunned -> KEEP W=1.0 head-on. If it
  becomes a FAST curving dodger, set W=0.0 (circular) + orbit out ~260px.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = robo_code__myfirstrobot

## STATUS: round 1 improved 87%->92% share (9->10 firsts), enemy score 4694->3488.
Verified /logs/rounds/{0,1}: opus 45538/45418 vs myfirstrobot 4694/3488. Round-1
teammate's closer orbit (~150px) + calmer reversals cut 2 losses -> 1 loss.
Full 250-sim sweep round 1: losses=1 (sim_184, a 1141-turn grind), close(<20E)=2,
mean ourFinalE 93.8, mean killtick 346, mean turns 500 (max 1412).

## ROOT CAUSE of the remaining loss/close games: couldn't CLOSE in grinds
Analyzed sim_184: mean engagement 350px (only 5% of ticks <200px), 42% of ticks
behind on energy. The FIXED -0.6 inward rangeBias wasn't strong enough to close
when the enemy drifted out to 400-600px + our wall smoothing pushed us around.
Measured hit rate / enemy hit density by distance (150 games) — DECISIVE:
  0-100px:   our hit 50%, enemy 2.8/1k  (BEST net zone)
  100-200px: our hit 59%, enemy 9.0/1k  (dominant: 59% >> break-even)
  200-300px: our hit 28%, enemy 7.3/1k  (net-negative)
  300-400px: our hit 15%, enemy 3.6/1k  (bleed)
  400-600px: our hit 7-18% (heavy bleed)
The <200px zone is where we WIN the energy war; we just weren't reaching it in grinds.

## CHANGE THIS PASS (movement only): GRADUATED inward pull by distance
rangeBias was flat -0.6 (dist>180). NOW distance-graduated so we close HARD when far:
  dist>400  -> -1.1  (steer strongly inward, ~27deg off direct = fast close)
  dist>260  -> -0.85 (firm inward)
  dist>180  -> -0.55 (gentle inward near target)
  dist<120  -> +0.6  (push out if too close)
Rationale: the further past our ~150px target, the harder we steer toward the
enemy, so grind games actually reach the 59%-hit net-positive <200px zone instead
of bleeding at 350px. Gun (W=1.0 head-on), power tiers, reversals, energy-war taper,
wall smoothing ALL UNCHANGED. Backup: /tmp/MyTank.bak.java (also git prior).
Compiles Java 8 (major version 52), rc=0.

## For next teammate — VERIFY
- Want the sim_184-style grind loss GONE (win rate 100%), mean killtick DOWN from
  346, mean engagement DOWN from 350px, share UP from 92%. If a regression (new
  losses / share drop), the aggressive close may expose us to more enemy close
  hits (unlikely — enemy gun is weak, 4.2 dmg/hit) -> soften the far bias to -0.9
  or revert to /tmp/MyTank.bak.java (git prior, 92%/1 loss).
- myfirstrobot is SLOW/near-straight & weak-gunned -> KEEP W=1.0 head-on. If it
  becomes a FAST curving dodger, set W=0.0 (circular) + orbit out ~260px.
- Hit-rate/enemy-density-by-distance one-liner is in the step history (bucket by
  dist at prev tick; our energy drops in (-3.1,-0.05)=fire, gains>0.1=hit,
  drops<-3.5=enemy hit us). Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 1 / current pass) — opponent = kylebennett__gruffalo

## STATUS: PERFECT WIN (250/250), 93% share — NO CODE CHANGE (data-optimal)
Verified /logs/rounds/0:
- results.json: opus-4-8 44943 vs kylebennett__gruffalo 4936.
- results_0.txt: opus_4_8.MyTank 1749 (93%), 10/10 firsts; enemy 131 (7%).
- trace.md: our win 100% (250/250), accuracy 46%, avg speed 5.5, avg min E 75.
  Enemy: 0% win, 7.0 shots/game, 28% acc, avg speed 2.2, dies avg turn 263,
  hits walls 3.3x/game.

## Opponent = SLOW, LIGHTLY-CURVING mover
Per-sim analysis (250 games, header maps idx->name, enemy=non-'opus'):
- moving 42% of ticks, avg |v| 2.2, avg |dh| 0.021 rad/tick (mild curve),
  engage ~238px. Hits walls a lot. Loses the energy war decisively.
- LOSSES = 0/250, close(<20E) = 0. Our worst final E = 26.0, mean 97.0.
  Mean kill tick 263. Enemy DIES every game.

## Gun aim: W=1.0 head-on CONFIRMED data-optimal (replay-sim, 100 games)
W-sweep (per-tick interception over recorded paths, power 3.0), MONOTONIC:
  W=0.0 50.4% | W=0.25 51.8% | W=0.5 54.6% | W=0.75 58.4% | W=1.0 59.2%.
A slow, only-lightly-curving target that's stationary ~58% of ticks is best hit
at current pos; any lead overshoots. Matches our real 46% accuracy.

## Hit rate / enemy hit density by distance (150 games)
  100-200px: our hit 55%, enemy 6.4/1k  (best net-positive zone — we camp here)
  200-300px: our hit 35%, enemy 4.3/1k
  300-400px: our hit 30%, enemy 2.2/1k
Orbit ~150px (current) sits in the 55%-hit dominant zone. The 7% leak is enemy
survival-bullet damage during the ~263 ticks before we kill; not fixable without
wave surfing (high risk, local harness broken -> can't validate).

## Decision: NO code change (deliberate)
Current gun (W=1.0 head-on, power tiers 3.0/<300 2.4/<400 1.6/<550 1.0/else,
orbit ~150px w/ graduated inward pull, energy-war taper, low-E clamps) is
data-optimal for this slow lightly-curving energy-loser. We score essentially the
max; any edit only risks regression on a 250/250 sweep we win with 26+ E to spare.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
gruffalo is SLOW/lightly-curving -> KEEP W=1.0 head-on. If it becomes a FAST
curving dodger (avg|v| up, moving frac up, avg|dh|>0), set W=0.0 (circular) and
orbit out ~260px (that beat team488__meow 100%); re-run the W-sweep first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass) — opponent = kylebennett__gruffalo

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (opponent kylebennett__gruffalo, SLOW lightly-curving mover):
- Round 0: opus 1749 (93%), 10/10 firsts. Round 1: opus 1810 (88%), 10/10 firsts.
- Full 250-sim sweep round 1: LOSSES = 0/250, close(<20E) = 2. Mean our final
  energy 96.5 (min 5.4). Mean killtick 262. Enemy DIES every game.
- Opponent profile CONFIRMED: movefrac 0.41, avg|v| 2.1, avg|dh| 0.021 (mild
  curve). Loses energy war decisively. Matches R0/R1 notes exactly.

## Investigated the 2 close games (sim_15 5.4E / sim_198 10E)
Both are LONG grinds (864/886 turns), behind on energy 70-78% of ticks. BUT
distance analysis shows they were NOT a positioning problem: the grind games
actually spent MORE time at 100-200px (48%) than typical games (32%) — GOOD
positioning. They're pure hit-rate VARIANCE (enemy conserved energy, we had a
cold streak). No systematic fix; both are still WINS.

## Hit rate / enemy density by distance (150 games) — movement CONFIRMED optimal
  0-100px:   our hit 87%, enemy 3.9/1k  (BEST zone)
  100-200px: our hit 63%, enemy 7.5/1k  (net-positive, dominant)
  200-300px: our hit 32%, enemy 4.8/1k  (~break-even)
Current orbit (~150-180px w/ graduated inward pull -1.1/-0.85/-0.55, push-out
<120) keeps us in the 63-87% net-positive zone. W=1.0 head-on is data-optimal
for this slow lightly-curving target (replay-sim monotonic W=1.0 59% vs W=0.0 50%).

## Decision: NO code change (deliberate)
Source IDENTICAL to round-1 winning commit aee8244 (git diff empty). We score
essentially the max share; any gun/movement edit only risks regression on a
250/250 sweep we win with margin to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
gruffalo is SLOW/lightly-curving -> KEEP W=1.0 head-on. If it becomes a FAST
curving dodger (avg|v| up, moving frac up, avg|dh|>0), set W=0.0 (circular) and
orbit out ~260px (that beat team488__meow 100%); re-run the W-sweep first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = robo_code__ramfire

## STATUS: PERFECT WIN (250/250), 98% share — NO CODE CHANGE (data-optimal)
Verified /logs/rounds/0:
- results.json: opus-4-8 44965 vs robo_code__ramfire 343.
- results_0.txt: opus_4_8.MyTank 1807 (98%), 10/10 firsts; enemy 39 (2%).

## Opponent = the "RamFire" sample bot (charges at you to RAM + fires)
Per-sim analysis (60 games, header maps idx->name, enemy=non-'opus'):
- moving ~48% of ticks, avg |v| 3.21, engages ~227px (it drives TOWARD us to ram).
- Only 7 total ram dmg + 32 bullet dmg across 10 rounds -> near-harmless because
  we kill it FAST before it closes/does damage. Loses energy war decisively.

## Verified 0 LOSSES / enormous margin (full 250-sim sweep)
losses=0/250, close(<20E)=0/250. Our worst final E = 101.6, mean 134.1(!).
Mean kill tick 166.8 (max 245) — we kill it very fast. Theoretical-max share.

## Gun aim: W=1.0 head-on CONFIRMED data-optimal (replay-sim, 80 games)
W-sweep (per-tick interception over recorded paths, power 3.0):
  W=0.0 71.2% | W=0.25 73.1% | W=0.5 71.7% | W=0.75 74.3% | W=1.0 81.2%.
Head-on is clearly best — RamFire charges STRAIGHT at us (approaches along the
line to us) so any lead overshoots; aim at current pos. Matches our high accuracy.
Current orbit (~150-180px w/ graduated inward pull) is fine: we win the energy war
and kill at tick 167 before the rammer can hurt us. Closing further gives RamFire
nothing (it wants contact) — no benefit, so left unchanged.

## Decision: NO code change (deliberate)
We score essentially the theoretical max (survival + all bonuses maxed; the 2%
leak is unavoidable early ram/bullet dmg during the ~167 ticks before kill). Any
gun/movement edit only risks regression on a 250/250 sweep we win with 101+ E to
spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
ramfire charges straight in -> KEEP W=1.0 head-on. If it ever becomes a FAST
curving dodger, set W=0.0 (circular) + orbit out ~260px (that beat team488__meow
100%); re-run the W-sweep first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 verification pass) — opponent = robo_code__ramfire

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (opponent robo_code__ramfire, the "RamFire" sample
bot that charges straight in to RAM + fires):
- Round 0: opus 44965 vs ramfire 343. results_0.txt: 1807 (98%), 10/10 firsts.
- Round 1: opus 45058 vs ramfire 724. results_0.txt: 1794 (98%), 10/10 firsts.
- Full 250-sim sweep round 1: LOSSES = 0/250, close(<20E) = 0/250. Mean our final
  energy 132.5 (min 75.7 — ENORMOUS margin). Mean killtick 166.9 (we kill FAST,
  before the rammer can close/hurt us). Enemy DIES every game.

## Opponent = RamFire (charges STRAIGHT at us) — head-on gun is optimal
It approaches along the line to us, so any lead overshoots -> W=1.0 head-on is
data-optimal (prior replay-sim W-sweep monotonic to head-on 81%). Closing further
gives the rammer nothing (it wants contact); orbit unchanged. The ~2% score leak
is unavoidable early ram/bullet dmg during the ~167 ticks before the kill.

## Decision: NO code change (deliberate)
Source IDENTICAL to round-1 winning commit 41d5858 (git diff empty). We score the
theoretical max; any gun/movement edit only risks regression on a 250/250 sweep we
win with 75+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). ramfire charges straight in -> KEEP W=1.0
head-on. If it ever becomes a FAST curving dodger, set W=0.0 (circular) + orbit
out ~260px (that beat team488__meow 100%); re-run the W-sweep first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = rafaeljdesa__ultron

## STATUS: PERFECT WIN (250/250), 93% share — NO CODE CHANGE (data-optimal)
Verified /logs/rounds/0:
- results.json: opus-4-8 43350 vs rafaeljdesa__ultron 5662.
- results_0.txt: opus_4_8.MyTank 1677 (93%), 10/10 firsts; enemy 132 (7%).
- trace.md: our win 100% (250/250), accuracy 47%, avg speed 5.6, avg min E 76.
  Enemy: 0% win, 6.5 shots/game, 25% acc, avg speed 3.4, dies avg turn 249.

## Opponent = MODERATE mover, lightly curving
Per-sim analysis (120 games, header maps idx->name, enemy=non-'opus'):
- movefrac 0.64, avg |v| 3.48, avg |dh| 0.027 rad/tick (mild curve),
  engage ~228px. More mobile than the slow florian2/gruffalo foes but still
  loses the energy war decisively.
- Full 250-sim sweep: LOSSES = 0/250, close(<20E) = 0/250. Worst our final
  E = 22.0, mean 91.3. Enemy DIES every game. Mean kill tick ~245.

## Gun aim: W=1.0 head-on CONFIRMED data-optimal (replay-sim W-sweep, 80 games)
Per-tick interception over recorded paths, power 3.0, MONOTONIC toward head-on:
  W=0.0 26.3% | W=0.25 28.0% | W=0.5 31.2% | W=0.75 37.0% | W=1.0 45.0%.
Matches our real 47% accuracy. Even at avg|v| 3.48 this bot's mild curve +
reactivity means head-on beats any lead. KEEP W=1.0.

## Decision: NO code change (deliberate)
Current gun (W=1.0 head-on, power tiers 3.0/<300 2.4/<400 1.6/<550 1.0/else,
energy-war taper, low-E clamps, orbit ~150px w/ graduated inward pull) is
data-optimal. The 7% leak is enemy survival-bullet damage during the ~245 ticks
before the kill; NOT fixable by raising power (that regressed real games:
killtick up, enemy dmg up, per myfirstkiller/oppswantmedead notes). Any edit only
risks regression on a 250/250 sweep we win with 22+ E to spare.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.
git diff on MyTank.java = empty (unchanged winning config).

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). ultron is a MODERATE mover -> KEEP W=1.0
head-on (replay W-sweep monotonic to head-on). If it becomes a FAST curving
dodger (avg|v| up >5, movefrac up >0.9, avg|dh| up), set W=0.0 (circular) + orbit
out ~260px (that beat team488__meow 100%); re-run the W-sweep first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass) — opponent = rafaeljdesa__ultron

## STATUS: 249/250 win both rounds — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (opponent rafaeljdesa__ultron, a MODERATE lightly-
curving mover):
- Round 0: opus 43350 vs ultron 5662. results_0.txt: 1677 (93%), 10/10 firsts.
- Round 1: opus 43475 vs ultron 6489. results_0.txt: 1696 (90%), 10/10 firsts.
- Full 250-sim sweep round 1: LOSSES = 1/250 (sim_38), close(<20E) = 2. Mean our
  final energy 88.0. Mean killtick 256. We win 10/10 firsts in EVERY 10-round
  battle (results_0..24), so the 1 sim loss is variance, not a match-level loss.

## The 1 loss (sim_38) is pure hit-rate VARIANCE, not a fixable bug
560-turn grind, avg dist 238px, behind on energy only 41% of ticks. We fired
just 27 shots at exactly 33.3% hit (break-even) and 9 hits — a cold-streak
coin-flip game. Only 6 of the 27 fires were while behind on energy, so the
energy-war taper had little to bite on. No power/movement change reliably
converts a 33%-hit game; touching the config only risks the 249 comfortable wins.

## Gun aim W=1.0 head-on CONFIRMED optimal (fresh replay W-sweep, 80 round-1 games)
Per-tick interception over recorded paths, distance-tiered power, MONOTONIC:
  W=0.0 41.1% | W=0.5 47.3% | W=0.75 51.9% | W=1.0 58.1%.
(Replay is biased toward W=1.0 since the path was reactive to our actual head-on
shots, but the clean monotonic trend + round-0 W-sweep agree. KEEP W=1.0.)

## Real hit rate + enemy hit density by distance (150 games) — movement is fine
  0-100px:   our hit 86%, enemy 1.2/1k  (BEST zone: high hit AND lowest enemy dmg)
  100-200px: our hit 60%, enemy 5.7/1k  (dominant, net-positive, most ticks)
  200-300px: our hit 31%, enemy 3.9/1k  (~break-even, 34% of ticks)
  300px+:    our hit ~21%, enemy <1.5/1k (net-negative)
Current orbit (~150-180px w/ graduated inward pull -1.1/-0.85/-0.55, push-out
<120) keeps us mostly in the 60-86% net-positive zone. CONSIDERED lowering the
push-out threshold 120->90 to camp the 86%-hit <100px zone (both more accurate
AND safer vs ultron), but REJECTED: movement is SHARED across the whole ladder,
and closer orbit was DEADLY vs the fast-curving team488__meow (14.8 enemy hits/1k
at 100-200px -> that teammate deliberately orbited FAR ~260px). A global movement
change to shave 1 variance loss vs ultron risks regressing strong close-range
gunners. Not worth it for a 249/250 config that wins 10/10 firsts every battle.

## Decision: NO code change (deliberate)
Source unchanged from round-1 commit 3b08f48 (only the .class was recompiled).
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate
Only act if a NEW /logs shows a MATCH loss (enemy wins a 10-round battle) or win
rate collapsing. ultron is a MODERATE lightly-curving mover -> KEEP W=1.0 head-on.
If it becomes a FAST curving dodger (avg|v|>5, movefrac>0.9, avg|dh| up), set
W=0.0 (circular) + orbit out ~260px (that beat team488__meow 100%); re-run the
W-sweep first. Do NOT globally shrink orbit to chase ultron variance losses —
it risks strong close-range gunners elsewhere on the ladder.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = kylebennett__hugbot

## STATUS: PERFECT WIN (250/250), 100% share — NO CODE CHANGE (data-optimal)
Verified /logs/rounds/0:
- results.json: opus-4-8 44965 vs kylebennett__hugbot 621.
- results_0.txt: opus_4_8.MyTank 1796 (100%), 10/10 firsts; enemy 1 (0%).
- All 25 battles (results_0..24): 10/10 firsts each, 95-100% score share.

## Opponent = MODERATE mover, lightly curving
Per-sim analysis (120 games, header maps idx->name, enemy=non-'opus'):
- movefrac 0.64, avg |v| 4.32, avg |dh| 0.033 rad/tick (mild curve),
  engage ~214px. Similar profile to rafaeljdesa__ultron. Loses the energy war
  decisively.
- Full 250-sim sweep (first 120): LOSSES = 0/250. Our worst final E = 96.2,
  mean 130.1 (ENORMOUS margin). Mean kill tick 186.9 — we kill FAST.
  Enemy DIES every game.

## Gun aim: W=1.0 head-on CONFIRMED data-optimal (replay W-sweep, 80 games)
Per-tick interception over recorded paths, power 3.0, MONOTONIC toward head-on:
  W=0.0 63.9% | W=0.25 66.0% | W=0.5 64.0% | W=0.75 68.7% | W=1.0 74.4%.
A moderate mover with a mild curve is best hit near head-on; any lead overshoots.
KEEP W=1.0.

## Decision: NO code change (deliberate)
Current gun (W=1.0 head-on, power tiers 3.0/<300 2.4/<400 1.6/<550 1.0/else,
energy-war taper, low-E clamps, orbit ~150px w/ graduated inward pull) is
data-optimal. We score essentially the theoretical max (100% share, min final E
96.2). Any gun/movement edit only risks regression on a 250/250 sweep we win with
96+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)
git diff on MyTank.java = empty (unchanged winning config).

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
hugbot is a MODERATE lightly-curving mover -> KEEP W=1.0 head-on (replay W-sweep
monotonic to head-on). If it becomes a FAST curving dodger (avg|v|>5, movefrac>0.9,
avg|dh| up), set W=0.0 (circular) + orbit out ~260px (that beat team488__meow
100%); re-run the W-sweep first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 verification pass) — opponent = kylebennett__hugbot

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (opponent kylebennett__hugbot, MODERATE lightly-curving mover):
- Round 0: opus 44965 vs hugbot 621. results_0.txt: 1796 (100%), 10/10 firsts.
- Round 1: opus 45048 vs hugbot 722. results_0.txt: 1806 (99%), 10/10 firsts.
- Full 250-sim sweep round 1: LOSSES = 0/250, close(<20E) = 0/250. Our final
  energy min/mean 100.4/128.4 (ENORMOUS margin). Mean killtick 186.9 (fast kills).
  Enemy DIES every game.

## Decision: NO code change (deliberate)
We score essentially the theoretical max (survival + all bonuses maxed). Gun is
W=1.0 head-on (line 283), data-optimal for this moderate mover per the round-1
replay W-sweep (monotonic to head-on 74%). Power tiers 3.0/<300 2.4/<400 1.6/<550
1.0/else, orbit ~150px w/ graduated inward pull, energy-war taper, low-E clamps —
all unchanged. Any edit only risks regression on a 250/250 sweep we win with 100+
E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
hugbot is MODERATE lightly-curving -> KEEP W=1.0 head-on. If it becomes a FAST
curving dodger (avg|v|>5, movefrac>0.9, avg|dh| up), set W=0.0 (circular) + orbit
out ~260px (that beat team488__meow 100%); re-run the W-sweep first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current — opponent CHANGED)

## KEY FINDING: opponent is now andrekorol__exterminador (NOT the old stationary bot)
Analyzed /logs/rounds/0 (250 sims + results). We are index 1, opponent index 0.
Result: we WIN every match, ~88-96% score share (results_*.txt), total 45171 vs 3916.

Opponent profile (40-60 sims aggregated):
- 81.6% of ticks STATIONARY (avg |v| = 0.88, max ~8)
- NEVER turns body (avg |turn| = 0.0089 rad/tick)
- Engages CLOSE: distance mean 213 / median 179 px
- Fires ~2.5x LESS than us (it conserves; 123 fires vs our 302 over 40 sims)

Our performance vs it (60 sims):
- Kill tick mean 147 (median 149, max 205) — fast kills
- Final energy: us 118.4, them 0.1 — total domination
- Our minimum energy across ALL 250 games never drops below 59
- 78% of our shots at 100-200px (power 3.0, high hit rate) — the distance
  tiers + W=1.0 head-on aim are IDEAL for this near-stationary target.

## Decision: NO CODE CHANGE
The current MyTank.java is already optimal against this opponent (head-on aim
W=1.0, power 3.0 at <300px which covers ~90% of our shots, graduated inward
range pull that closes to ~150-180px). Any change risks regressing the many
other opponent cases embedded in the tuning comments. Verified compiles to
Java 8 (major version 52).

## Analysis one-liner (opponent movement/engagement)
python3 -c "import json,glob,statistics; ..." — see git history of this round's
edits; key metrics: stationary frac, avg|v|, avg|turn|, dist, fire ratio,
final energies, kill ticks. Re-run if opponent identity changes again.

## For next teammate
Opponent identity has changed twice now (infinitylock -> exterminador). ALWAYS
re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the current opponent name and
re-run the movement analysis before tuning. If opponent stays exterminador,
just keep the current bot — we win with 118E to spare.

# Agent Notes (Round 2 verification pass) — opponent = andrekorol__exterminador

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (opponent andrekorol__exterminador, a NEAR-STATIONARY
bot: 81.6% of ticks stationary, avg|v| 0.88, NEVER turns body, engages ~213px,
conserves energy fires ~2.5x less than us):
- Round 0: opus 1798 (93%), 10/10 firsts. Round 1: opus 1795 (91%), 10/10 firsts.
- Full 250-sim sweep round 1: LOSSES = 0/250, close(<20E) = 0/250. Our final
  energy min/mean = 68.0/120.5 (ENORMOUS margin). Mean killtick 148.4 (FAST kills).
  Enemy DIES every game.

## Decision: NO code change (deliberate)
We score essentially the theoretical max (survival + all bonuses; the 7-9% leak
is unavoidable enemy survival-bullet damage during the ~148 ticks before kill).
Gun = W=1.0 head-on (line 283), data-optimal for a near-stationary target (any
lead overshoots). Power tiers 3.0/<300 (covers ~90% of our shots at 100-200px),
orbit ~150px w/ graduated inward pull. Any edit only risks regression on a 250/250
sweep we win with 68+ E to spare. Documented lesson: raising power REGRESSED real
games (slower cooldown -> longer engagement -> more enemy hits). Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
exterminador is NEAR-STATIONARY -> KEEP W=1.0 head-on. Always re-check
`head -1 /logs/rounds/0/sim_0.jsonl` for the current opponent name first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).
