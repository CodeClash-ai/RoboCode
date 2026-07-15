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
