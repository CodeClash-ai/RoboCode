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

# Agent Notes (Round 1 / current pass) — opponent = robo_code__tracker

## STATUS: PERFECT WIN (250/250), 91% share — NO CODE CHANGE (data-optimal)
Verified /logs/rounds/0 (opponent robo_code__tracker, the "Tracker" sample bot —
near-STATIONARY: movefrac 0.20, avg|v| 1.01, avg|dh| 0.018, engages ~215px):
- results.json: opus-4-8 45217 vs robo_code__tracker 4269.
- results_0.txt: opus_4_8.MyTank 1821 (91%), 10/10 firsts; enemy 177 (9%).
- Full 250-sim sweep: LOSSES = 0/250, close(<20E) = 0/250. Our final energy
  min/mean = 64.8/117.7 (huge margin). Mean killtick 149 (FAST). Enemy DIES
  every game.

## Gun aim W=1.0 head-on CONFIRMED data-optimal (fresh replay W-sweep, 80 games)
Per-tick interception over recorded paths, power 3.0, MONOTONIC toward head-on:
  W=0.0 69.5% | W=0.25 70.6% | W=0.5 72.3% | W=0.75 73.6% | W=1.0 74.8%.
A near-stationary target is best hit at current pos; any lead overshoots. KEEP W=1.0.

## Decision: NO code change (deliberate)
We score essentially the theoretical max (survival + all bonuses; the 9% leak is
unavoidable enemy survival-bullet damage during the ~149 ticks before the kill).
Any gun/movement edit only risks regression on a 250/250 sweep we win with 64+ E
to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
tracker is near-stationary -> KEEP W=1.0 head-on. Always re-check
`head -1 /logs/rounds/0/sim_0.jsonl` for the current opponent name first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass, this pass) — opponent = robo_code__tracker

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE
Re-verified /logs/rounds/{0,1} (opponent robo_code__tracker, near-stationary
"Tracker" sample bot):
- Round 0: opus 45217 vs tracker 4269 (91% share), 250/250, 10/10 firsts.
- Round 1: opus 45049 vs tracker 3720, 250/250 firsts.
- Full 250-sim sweep round 1: LOSSES = 0/250, close(<20E) = 0/250. Our final
  energy min/mean = 39.0/120.4 (huge margin). Mean killtick 149.1 (fast kills).
  Enemy DIES every game.

## Decision: NO code change (deliberate)
Bot compiles clean to Java 8 (major version 52), git diff on MyTank.java = empty.
W=1.0 head-on is data-optimal for a near-stationary target (any lead overshoots;
prior replay W-sweep monotonic to head-on 74.8%). The ~9% score leak is
unavoidable enemy survival-bullet damage during the ~149 ticks before we kill it.
Raising power to kill faster REGRESSES (longer cooldown -> longer engagement ->
MORE enemy hits, per myfirstkiller/exterminador notes). Any edit only risks
regression on a 250/250 sweep we win with 39+ E to spare.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
tracker is near-stationary -> KEEP W=1.0 head-on. Always re-check
`head -1 /logs/rounds/0/sim_0.jsonl` for the current opponent name first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = luke_f_w__nagisphere

## STATUS: PERFECT WIN (250/250), 96% share — NO CODE CHANGE (data-optimal)
Verified /logs/rounds/0 (opponent luke_f_w__nagisphere, a STATIONARY sitting duck):
- results.json: opus-4-8 44175 vs luke_f_w__nagisphere 1941.
- results_0.txt: opus_4_8.MyTank 1760 (98%), 10/10 firsts; enemy 29 (2%).
- trace.md: our win 100% (250/250), accuracy 96%(!), avg speed 5.5, avg min E 94.
  Enemy: 0% win, 4.6 shots/game, 19% acc, avg speed 0.0 (NEVER moves), dies turn 139.

## Opponent = STATIONARY (avg speed 0.0, never moves)
Full 250-sim sweep: LOSSES = 0/250, close(<20E) = 0/250. Our final energy
min/mean = 96.1/129.4 (ENORMOUS margin). Mean killtick 138.7 (fast kills).
Enemy DIES every game. Games are short (avg 290 turns). This is the theoretical
max: our 96% accuracy head-on gun mows the sitting duck.

## Decision: NO code change (deliberate)
W=1.0 head-on is data-optimal for a stationary target (any lead overshoots).
The ~2-4% score leak is unavoidable enemy survival-bullet damage (it fires ~4.6
low-acc shots/game) during the ~139 ticks before we kill it. Raising power to
kill faster REGRESSES (longer cooldown -> longer engagement -> MORE enemy hits,
per myfirstkiller/exterminador/tracker notes). Any edit only risks regression on
a 250/250 sweep we win with 96+ E to spare. git diff on MyTank.java = empty.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
nagisphere is STATIONARY -> KEEP W=1.0 head-on. Always re-check
`head -1 /logs/rounds/0/sim_0.jsonl` for the current opponent name first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass, this pass) — opponent = luke_f_w__nagisphere

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE
Re-verified /logs/rounds/{0,1} (opponent luke_f_w__nagisphere, STATIONARY sitting duck):
- Round 0: opus 44175 vs nagisphere 1941 (results.json). Round 1: opus 44028 vs 1805.
- Full 250-sim sweep round 1: LOSSES = 0/250, close(<20E) = 0/250. Our final
  energy min/mean = 100.8/129.7 (ENORMOUS margin). Mean killtick 140.6 (fast kills).
  Enemy DIES every game.

## Decision: NO code change (deliberate)
W=1.0 head-on is data-optimal for a stationary target (any lead overshoots).
The ~2-4% score leak is unavoidable enemy survival-bullet damage during the ~140
ticks before the kill. Raising power to kill faster REGRESSES (longer cooldown ->
longer engagement -> MORE enemy hits, per myfirstkiller/exterminador/tracker notes).
Any edit only risks regression on a 250/250 sweep we win with 100+ E to spare.
git diff on MyTank.java = empty. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
nagisphere is STATIONARY -> KEEP W=1.0 head-on. Always re-check
`head -1 /logs/rounds/0/sim_0.jsonl` for the current opponent name first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = robo_code__velocirobot (TOUGH FOE, 11 losses)

## KEY FINDING: velocirobot is a FAST NEAR-STRAIGHT mover with a GOOD gun — first real fight in a while
Round 0 result (BEFORE my change): opus-4-8 43310 vs robo_code__velocirobot 7295.
results_0.txt: opus 1774 (88%), 10/10 firsts BUT trace.md WIN RATE 96% (239/250)
-- we LOSE 11 games. Enemy accuracy 36% > our 32%. Our avg min energy only 58.
Games are LONG (avg 520, max 914). This is a genuinely competitive opponent.

## Opponent profile (120 sims; header maps idx->name, enemy=non-'opus')
- movefrac 0.94 (almost always moving), avg |v| 4.35 (FAST), avg |dh| 0.020
  (near-STRAIGHT, only lightly curving), engages CLOSE (dist mean 219 / median 194).
  Fires ~2.5x less than us but its gun is accurate. It out-trades us in close grinds.

## The 7 loss games (round 0): ALL long close-range energy-war GRINDS
sim_118/144/155/181/185/188/194: 734-836 turns, avg dist 174-221px, behind on
energy 54-96% of ticks. We bled out first because our hit rate at close range
wasn't high enough to win the energy war fast enough.

## CHANGE THIS PASS (gun aim): W = 1.0 (head-on) -> 0.25 (partial lead)
The gun was left at W=1.0 head-on from the SLOW florian2 match. That is WRONG for
this FAST near-straight mover. Replay W-sweep (per-tick interception over recorded
paths, 2 independent 80-game slices):
  slice A: W0.0=43.7% W0.25=47.0% W0.5=39.4% W0.75=39.4% W1.0=42.8%
  slice B: W0.0=43.5% W0.1=44.6% W0.25=46.6% W0.35=45.2% W0.5=41.4%
CLEAN peak at W=0.25 (~47%) vs head-on ~43%. A fast, mostly-straight mover needs
a partial lead (full lead overshoots its mild curves/reversals; head-on trails).
NOTE: the replay is biased TOWARD head-on (enemy path was reactive to our ACTUAL
W=1.0 shots), yet W=0.25 STILL wins -> strong signal it's the right aim.
Damage/net-energy replay (120 games, distance-tiered power): W=1.0 dmg 25778
net +3399 -> W=0.25 dmg 29161 (+13%) net +5225 (+54%). BOTH damage and net energy
UP -> no energy-war downside; directly attacks the close-grind losses.

## Movement: LEFT UNCHANGED (deliberate — data says current orbit is right)
Net-energy-by-distance (measured, 150 games): 0-100px NET -56/1k, 100-200px -79/1k
(BEST), 200-300px -153/1k, 300-400px -94/1k. Closing to <200px is correct (our
39% hit rate there beats 17% at 200-300px, and it's the least-negative zone). The
current orbit (~150-180px w/ graduated inward pull -1.1/-0.85/-0.55, push-out
<120) already camps the right zone. Note enemy hit density is HIGH up close
(23.6/1k @ 0-100, 15/1k @ 100-200) so do NOT push closer than ~150px vs this good
gun. The gun (hit rate) is the lever, not movement.

## Compile: javac --release 8 ... -> major version 52 (Java 8), rc=0.
## Backup of prior source (W=1.0): /tmp/MyTank.bak.java.

## For next teammate — VERIFY
- Want NEW /logs win rate ABOVE 96% (ideally 100%), our accuracy UP from 32%,
  fewer/no long grind losses, our avg min-E UP from 58. If it DROPPED, the W=0.25
  lead may have hurt (revert to W=1.0 / /tmp/MyTank.bak.java, which won 96%).
- velocirobot is a FAST near-straight mover -> KEEP W=0.25 (or re-run the W-sweep
  on >=2 slices if it changes profile). If it becomes a SLOW mover, raise W toward
  1.0; if a HEAVILY-curving dodger (avg|dh|>0.06), set W=0.0 (circular).
- The remaining lever if still losing grinds is WAVE SURFING (enemy gun 36%
  accurate -> dodging its close-range bullets is the biggest untapped win, but
  high-risk; local harness broken, trust /logs only).
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass) — opponent = robo_code__velocirobot

## STATUS: THE ROUND-1 W=0.25 GUN CHANGE WORKED BIG — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (opponent robo_code__velocirobot, a FAST near-straight
mover with a GOOD gun — first competitive foe in a while):
- Round 0 (OLD W=1.0 head-on, tuned for slow florian2): opus 43310 vs velocirobot
  7295. results_0.txt: 1774 (88%), 10/10 firsts BUT trace WIN RATE 96% — LOST 11
  games (long close-range energy-war grinds). Enemy accuracy 36% > our 32%.
- Round 1 (prior teammate switched gun aim to W=0.25 partial lead): opus 44478 vs
  velocirobot 3905. HUGE improvement:
    * win rate 96% -> 100% (full 250-sim sweep: LOSSES = 0/250, close(<20E)=0)
    * enemy score 7295 -> 3905 (nearly HALVED)
    * our final energy min/mean = 41.0/104.0 (comfortable margin)
    * mean killtick 238 (down from ~long grinds), mean turns 390 (max 669, was 914)
  The W=0.25 change (partial lead for a fast near-straight mover) is correct and
  real-result-confirmed.

## Replay-sim on ROUND-1 logs is BIASED — do NOT use it to raise W back to head-on
Ran a W-sweep on round-1 logs (80 games): it shows W=1.0=50% > W=0.75=42% >
W=0.25=25% > W=0.0=21.5% — MONOTONIC toward head-on. This is the KNOWN reactivity
bias (the enemy's round-1 path was reactive to our ACTUAL W=0.25 shots, so the
replay artificially favors head-on). The REAL cross-round game result is decisive
and OPPOSITE: W=1.0 LOST 11 games / enemy 7295, W=0.25 won 250/250 / enemy 3905.
DO NOT switch back to head-on off the biased replay. This is the same trap that
regressed us to 83% vs robo_code__crazy (flat power) and slower kills vs
myfirstkiller (raised power). Trust /logs win rate, not the replay's hit numbers.

## Decision this pass: NO code change (deliberate)
Source is IDENTICAL to the round-1 winning config (W=0.25 partial lead at line
283, power tiers 3.0/<300 2.4/<400 1.6/<550 1.0/else, energy-war taper, low-E
clamps, orbit ~150-180px w/ graduated inward pull). git diff on MyTank.java =
empty. Any gun/movement edit only risks regression on a 250/250 sweep we now win
with 41+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). velocirobot is a FAST near-straight mover ->
KEEP W=0.25 partial lead. Do NOT raise W to head-on off the biased replay-sim.
If it becomes SLOW (avg|v| down), raise W toward 1.0; if HEAVILY-curving
(avg|dh|>0.06), set W=0.0 (circular). Re-run the W-sweep on >=2 slices first BUT
weight the REAL cross-round game result far above the replay hit numbers.
The remaining lever if grinds return is WAVE SURFING (enemy gun 36% accurate) —
high-risk, local harness broken, trust /logs only.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = avsthiago__sadbot

## KEY FINDING: opponent CHANGED to a SLOW lightly-curving mover — gun was misconfigured (W=0.25 leftover from FAST velocirobot)
Round 0 result (BEFORE my change): opus-4-8 44617 vs avsthiago__sadbot 4656.
results_0.txt: opus 1775 (90%), 10/10 firsts. Full 250-sim sweep: 249 wins,
1 "loss" (sim_83) which is actually a TURN-LIMIT TIMEOUT — we were DOMINATING
(our final E 104.7 vs enemy 8.4 at 281 turns) but the round ended before the
kill. Faster kills convert it to a full win + more score.

## Opponent profile (250 sims; header maps idx->name, enemy=non-'opus')
- movefrac 0.36, avg |v| 2.0 (SLOW), avg |dh| 0.031 rad/tick (lightly curving),
  engages ~229px. Loses the energy war decisively (min our final E 41, mean 106.5).
  Kill tick mean 203. Games avg 354 turns (max 543).

## CHANGE THIS PASS: gun aim W = 0.25 -> 1.0 (head-on)
The gun was left at W=0.25 (partial lead) from the FAST velocirobot match. That
is WRONG for this SLOW lightly-curving mover. W-sweep (per-tick interception over
recorded paths, 2 independent 80-game slices), CLEAN MONOTONIC toward head-on:
  slice A: W0.0 52.3% W0.25 52.9 W0.5 54.1 W0.75 59.0 W1.0 63.8
  slice B: W0.0 52.5% W0.25 52.6 W0.5 53.3 W0.75 57.0 W1.0 64.3
Head-on wins by ~11 points. NOTE: the replay is biased TOWARD the OLD aim (W=0.25,
since the enemy path was reactive to our actual W=0.25 shots), yet head-on STILL
wins big -> very strong signal it's correct (bias would favor W=0.25, not against).
Damage/net-energy replay (120 games, distance-tiered power): W=0.25 dmg 22001
net +4662 -> W=1.0 dmg 26556 (+21%) net +7251 (+55%). BOTH damage AND net energy
UP -> no energy-war downside; faster kills convert the sim_83 turn-limit timeout.
Power tiers (3.0/<300 2.4/<400 1.6/<550 1.0/else), movement (orbit ~150px w/
graduated inward pull), energy-war taper, low-E clamps ALL UNCHANGED.
Compiles Java 8 (major version 52), rc=0. Backup of prior source: /tmp/MyTank.bak.java.

## For next teammate — VERIFY
- Want NEW /logs win rate 100% (the sim_83 turn-limit timeout GONE), killtick DOWN
  from 203, score share UP from 90%. If it DROPPED, sadbot may have become faster
  -> re-run /tmp/wsweep.py (W-sweep, 2 slices) and lower W. sadbot is SLOW/lightly-
  curving -> head-on (W=1.0) is data-optimal. If it becomes a FAST curving dodger
  (avg|v|>5, movefrac>0.9, avg|dh|>0.06), set W=0.0 (circular) + orbit out ~260px.
- Tool: /tmp/wsweep.py (W-sweep) and /tmp/dmg.py (damage/net-energy). Rebuild from
  this note if lost. Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the
  current opponent name first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 verification pass) — opponent = avsthiago__sadbot

## STATUS: THE ROUND-1 W=1.0 HEAD-ON CHANGE WORKED — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (opponent avsthiago__sadbot, SLOW lightly-curving
mover: movefrac 0.36, avgV 1.89, avg|dh| 0.031, engages ~237px, loses energy war):
- Round 0 (before change, W=0.25 leftover from FAST velocirobot): opus 44617 vs
  sadbot 4656. 90% share, 10/10 firsts BUT 1 turn-limit timeout (sim_83: we were
  DOMINATING 104.7E vs 8.4E at 281 turns, round ended before kill).
- Round 1 (prior teammate switched gun aim to W=1.0 head-on): opus 44592 vs sadbot
  4368. results_0.txt: opus_4_8.MyTank 1788 (90%), 10/10 firsts. Full 250-sim
  sweep: LOSSES = 0/250, close(<20E) = 0/250 (the sim_83 timeout is GONE — faster
  kills). Our final energy min/mean = 35.6/108.9. Mean killtick 193.7 (down from
  203). Enemy DIES every game.

## Gun aim W=1.0 head-on CONFIRMED optimal (per round-1 W-sweep in prior notes)
sadbot is a SLOW lightly-curving target -> best hit at current pos; any lead
overshoots (W-sweep 2 slices monotonic to head-on: W=1.0 ~64% vs W=0.25 ~53%).
The ~10% score leak is unavoidable enemy survival-bullet damage during the ~194
ticks before the kill. Raising power to kill faster REGRESSES real games (longer
cooldown -> longer engagement -> MORE enemy hits, per myfirstkiller/exterminador/
tracker notes). NOT fixable without wave surfing (high risk, local harness broken).

## Decision: NO code change (deliberate)
Source IDENTICAL to round-1 winning commit 5f8896c (git diff on MyTank.java =
empty). W=1.0 head-on (line 283), power tiers 3.0/<300 2.4/<400 1.6/<550 1.0/else,
orbit ~150px w/ graduated inward pull (-1.1/-0.85/-0.55, push-out <120), energy-war
taper, low-E clamps. Any edit only risks regression on a 250/250 sweep we win with
35+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). sadbot is SLOW/lightly-curving -> KEEP W=1.0
head-on. If it becomes a FAST mover (avg|v|>4), lower W toward 0.25; if HEAVILY-
curving (avg|dh|>0.06), set W=0.0 (circular) + orbit out ~260px. Always re-check
`head -1 /logs/rounds/0/sim_0.jsonl` for the current opponent name first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = looklazy__chilibot

## STATUS: 249/250 win, 94% share — NO CODE CHANGE (data-optimal)
Opponent CHANGED to looklazy__chilibot. Verified /logs/rounds/0:
- results.json: opus-4-8 44194 vs looklazy__chilibot 3645 (94%/6%).
- results_0.txt: opus_4_8.MyTank 1756 (94%), 10/10 firsts; enemy 109 (6%).

## Opponent = SLOW near-STRAIGHT-LINE mover
Per-sim analysis (250 games, header maps idx->name, enemy=non-'opus'):
- movefrac 0.315, avg |v| 1.46 (SLOW), avg |dh| 0.0 (NEVER turns body — pure
  straight-line back/forth with pauses), engages ~232px.
- Full 250-sim sweep: 249 wins, 1 non-win = TURN-LIMIT TIMEOUT (sim_244, 355
  turns: we were DOMINATING 91.6E vs enemy 7.7E, round ended before the kill —
  NOT a real loss). close(<20E) = 0. Our final E min/mean = 41.2/110.6. Mean
  killtick 205. Enemy DIES (finalE ~0) in every real game.

## Gun aim W=1.0 head-on CONFIRMED data-optimal (fresh replay W-sweep, 80 games)
Per-tick interception over recorded paths, power 3.0, MONOTONIC toward head-on:
  W=0.0 59.2% | W=0.25 60.8% | W=0.5 63.0% | W=0.75 67.2% | W=1.0 72.1%.
A slow near-straight-line target that pauses ~68% of ticks is best hit at current
pos; any lead overshoots. KEEP W=1.0.

## Decision: NO code change (deliberate)
Source is IDENTICAL to prior winning config (git diff on MyTank.java = empty).
W=1.0 head-on (line 283), power tiers 3.0/<300 2.4/<400 1.6/<550 1.0/else, orbit
~150px w/ graduated inward pull, energy-war taper, low-E clamps. The ~6% score
leak is unavoidable enemy survival-bullet damage during the ~205 ticks before the
kill. Raising power to kill faster REGRESSES real games (longer cooldown -> longer
engagement -> MORE enemy hits — documented vs myfirstkiller/exterminador/tracker).
Any edit only risks regression on a 249/250 sweep we win with 41+ E to spare.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate
Only act if a NEW /logs shows win rate dropping or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). chilibot is SLOW/near-straight -> KEEP W=1.0
head-on. If it becomes a FAST mover (avg|v|>4), lower W toward 0.25; if HEAVILY-
curving (avg|dh|>0.06), set W=0.0 (circular) + orbit out ~260px. Always re-check
`head -1 /logs/rounds/0/sim_0.jsonl` for the current opponent name first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass, this pass) — opponent = looklazy__chilibot

## STATUS: 249/250 win — NO CODE CHANGE (deliberate)
Verified /logs/rounds/{0,1} (opponent looklazy__chilibot, SLOW near-straight-line
mover: movefrac 0.34, avgV 1.56, avg|dh| 0.0006 = NEVER turns body, engages ~228px):
- Round 0: opus 44194 vs chilibot 3645 (94% share), 10/10 firsts.
- Round 1: opus 44249 vs chilibot 4036 (results_0.txt 87% in that 10-round sample),
  10/10 firsts. Full 250-sim sweep: LOSSES = 1/250 (sim_154), close(<20E)=1.
  Our final E mean 108.9, mean killtick 210, turns mean 362 (max 771).

## The 1 loss (sim_154) = a 771-turn DISTANT-ENGAGEMENT grind (variance)
Analyzed: mean dist 294px (vs typical ~228), behind on energy 85% of ticks.
We + enemy each landed ~10 hits, but enemy fired 26 vs our 38 at higher efficiency
in the far zone. Distance histogram: LOSS spent only 59% of ticks <200px + 25% at
300-600px, vs typical wins ~73% <200px. Crucially, 16% of the loss game we were in
the "dist>400 AND behind on energy" NO-FIRE gate (lines 310-313) — conceding free
enemy damage while dealing none. The enemy's straight-line gun out-traded us in
that distant grind we couldn't escape.

## Why NO change (followed the README's proven discipline)
Considered: (a) allow low-power far shots when behind (contest instead of conceding)
— but README documents net-negative far firing vs an energy-conserving mover is
what CAUSES these grind losses (the gate is deliberate; chilibot ~20% hit at 400px+).
(b) close harder in grinds — but movement is GLOBAL across the ladder and the README
warns closer orbit was DEADLY vs the fast-curving team488__meow (14.8 enemy hits/1k
@ 100-200px). A change to shave 1 variance loss vs chilibot risks regressing strong
close-range gunners elsewhere. We win 10/10 firsts in EVERY 10-round battle, so the
1 sim loss is not a match-level loss. Not worth the regression risk.
Gun W=1.0 head-on CONFIRMED optimal (prior W-sweep monotonic to head-on 72.1%;
chilibot is slow near-straight -> any lead overshoots).

## Compile: git diff on MyTank.java = empty. Re-verified:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows a MATCH loss (enemy wins a 10-round battle) or win
rate collapsing. chilibot is SLOW/near-straight -> KEEP W=1.0 head-on. If the
grind losses ever RISE to a match-level threat, the targeted lever is the far-range
fire gate: allow power ~0.1 far shots when behind (tiny per-miss cost, contests the
free-damage concession) — but validate vs the ladder, NOT just chilibot. Always
re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the current opponent name first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = robo_code__spinbot (COMPETITIVE FOE, 30 losses)

## KEY FINDING: opponent is the "SpinBot" sample bot — FAST, HEAVILY-CURVING (it spins in a circle)
Round 0 result (BEFORE my change): opus-4-8 40475 vs robo_code__spinbot 10217.
results_0.txt: opus 1745 (86%), 10/10 firsts BUT trace.md WIN RATE 88% (220/250)
-- we LOST 30 games! Our accuracy only 28%, enemy 29%. Games LONG (avg 563).
This is a genuinely competitive opponent (enemy score 10217 = 20% share, our
highest-scoring foe in a while).

## Opponent profile (120 sims; header maps idx->name, enemy=non-'opus')
- movefrac 0.95, avg |v| 4.68 (FAST), avg |dh| 0.087 rad/tick (STRONG curve --
  it drives in a continuous circle), engages CLOSE (dist mean 218 / median 197).
  Fires ~2.5x less than us (7.8 vs 25 shots/game) but its gun is decent.

## WHY WE LOST 30 GAMES: head-on gun was net-energy-NEGATIVE vs the spinner
Gun was left at W=1.0 (head-on) from the SLOW avsthiago__sadbot match. That is
WRONG for a fast heavily-curving mover. Replay damage/net-energy sim (100 games):
  OLD head-on: dmg 16766, NET ENERGY -24 (we were BLEEDING -> the 30 losses).
  NEW circular: dmg 28670 (+71%), NET +6681 (we GAIN energy). Huge flip.

## CHANGE 1 (gun aim): W = 1.0 (head-on) -> 0.0 (full CIRCULAR lead)
The circular predictor already existed in code (steps enemy forward applying
smoothed EMA turn rate). Just set W=0.0 to use it. W-sweep (per-tick interception
over recorded paths, TWO independent 80-game slices), CLEAN + robust:
  slice A: headon 33.3% | W0.25circ 45.5 | W0.0circ 55.9 | W0.0linear 21.0
  slice B: headon 34.3% | W0.25circ 45.7 | W0.0circ 56.5 | W0.0linear 20.7
Circular targeting nearly DOUBLES hit rate vs head-on. (Same fix that beat
team488__meow -- another fast curving dodger -- 93%->100%.)

## CHANGE 2 (movement): orbit ~150px -> ~250px
With circular targeting our hit rate by distance (100 games):
  0-100px 69% | 100-200px 58% | 200-300px 61%(!) | 300-400px 41% | 400+ ~33%.
Our hit at 200-300px (61%) is even HIGHER than 100-200px (58%). AND SpinBot's gun
is DANGEROUS up close (enemy hit density 17.5/1k @0-100, 6.3/1k @100-200) but
NEARLY HARMLESS at 200-300px (0.9/1k). So orbiting WIDER to ~250px is strictly
better: same/better hit rate AND ~7x fewer enemy hits. rangeBias retuned:
  >450 -> -1.0, >320 -> -0.6, >250 -> -0.3, <220 -> +0.5 (push out of kill zone).
Power tiers UNCHANGED (3.0/<300 covers the 250px orbit at 61% hit = strongly
net-positive), fire gates + energy-war taper UNCHANGED. Backup: /tmp/MyTank.bak.java.
Compiles Java 8 (major version 52), rc=0.

## For next teammate — VERIFY
- Want NEW /logs win rate ABOVE 88% (ideally 97%+), our accuracy UP from 28%,
  enemy score DOWN from 10217, fewer/no losses. If it DROPPED, first suspect the
  wider orbit (if SpinBot's gun is actually good at 250px, pull back to ~180px:
  thresholds 350/250/200/<160) OR the circular gun (unlikely -- W-sweep is very
  clean + matches the team488__meow success). Revert to /tmp/MyTank.bak.java
  (W=1.0, ~150px) which won 88% if a clear regression.
- spinbot is a FAST heavily-curving mover -> KEEP W=0.0 (circular). Do NOT switch
  to head-on off a biased replay-sim (that trap regressed us elsewhere; the
  cross-round REAL win rate is decisive). If it becomes SLOW/straight, raise W.
- The remaining lever if grinds return is WAVE SURFING (high-risk, harness broken).
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the opponent name first.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 vs robo_code__spinbot — CURRENT)

## STATUS: DOMINATING — 100% win rate, no code change needed
Opponent this rung = robo_code__spinbot (SpinBot: drives in a circle at max
velocity 8, avgV 4.68, avgDH 0.087 rad/tick, fires while spinning).
- Round 1 result: won ALL 25 matches (10 rounds each), score 43512 vs 1588.
  Each match ~96-98% score share, 10/10 firsts.
- Our median final energy at round end = 131.6 (min 52.8, max 140.8). We take
  almost no damage; enemy dies every round.

## Why the bot already crushes SpinBot (already tuned by prior teammate)
- Gun: W=0.0 CIRCULAR targeting (tracks enemyTurnRate). Nearly DOUBLES hit rate
  vs head-on against this curving mover (~56% vs ~34% in W-sweep sim).
- Movement: orbit ~250px. Tick-distance analysis (this round) confirms we spend
  most ticks (12249) at 200-300px — SpinBot's HARMLESS zone (enemy hit density
  0.9/1k) — vs its deadly close range (17.5/1k at 0-100, 6.3/1k at 100-200).
- Power tiers by distance keep energy-war net-positive; SpinBot loses it badly.

## Decision: NO CODE CHANGE
The MyTank.java gun/movement is already opponent-specifically tuned for spinbot
(see the W= line ~283 and rangeBias comments ~373). Changing it risks a
regression with zero upside (we already win 100% at ~97% share). Verified it
still compiles to Java 8 (major version 52) this round.

## Analysis one-liners (this round)
# Confirm i=1 = us (opus), i=0 = enemy; final energy per round:
python3 -c "import json,glob,statistics;files=sorted(glob.glob('/logs/rounds/1/sim_*.jsonl'));my=[[u for u in [d for d in [json.loads(l) for l in open(fn)] if 'u' in d][-1]['u'] if u['i']==1][0]['e'] for fn in files];print('median',statistics.median(my),'min',min(my))"

## For next teammate
If a NEW opponent appears next rung, check its movement:
  python3 -c "import json,glob;fn=sorted(glob.glob('/logs/rounds/N/sim_*.jsonl'))[0];[print([u for u in d['u'] if u['i']==0][0]['v']) for d in [json.loads(l) for l in open(fn)][:25] if 'u' in d]"
Then pick W (0.0=full lead/circular for fast curvers, 1.0=head-on for slow/
oscillators) and rangeBias orbit distance based on where the enemy's gun is weak.

# Agent Notes (Round 1 / current pass) — opponent = iagomonteiro13579__npcsniper (COMPETITIVE, 15 losses)

## KEY FINDING: gun was misconfigured (W=0.0 circular leftover from spinbot) + orbit too far
Round 0 result (BEFORE my change): opus-4-8 39031 vs npcsniper 7379 (16% share!).
results_0.txt: opus 1611 (89%), 10/10 firsts BUT full 250-sim sweep: 15 LOSSES,
21 close(<20E), our final E mean only 59.4, games LONG (avg 620, max 1044),
killtick mean 450. This is a genuinely competitive foe (highest enemy share in a while).

## Opponent profile (250 sims; header maps idx->name, enemy=non-'opus')
- movefrac 0.72, avg|v| 4.31 (moderately fast), avg|dh| 0.0345 (MILD curve — NOT
  a heavy spinner like spinbot's 0.087), engages ~297px. Fires a decent gun.

## ROOT CAUSE of the 15 losses: net-energy-NEGATIVE firing at 200-400px
Measured OUR hit rate + enemy density + NET bullet energy by distance (250 games):
  0-100px:   hit 91%, enemy 12.9/1k, NET -39   (enemy gun deadly close)
  100-200px: hit 66%, enemy  8.7/1k, NET +270  (ONLY net-positive zone!)
  200-300px: hit 26%, enemy  5.8/1k, NET -6254 (CATASTROPHIC — we fired 3358 shots at 26%)
  300-400px: hit 18%, enemy  6.9/1k, NET -3247
We orbited ~250px (spinbot config) -> spent most ticks in the -6254 zone, bleeding
the energy war -> the 15 losses + long grinds.

## CHANGES THIS PASS (both data-supported)
1. GUN aim W: 0.0 (circular) -> 1.0 (HEAD-ON). W-sweep 2 independent 80-game
   slices (per-tick interception over recorded paths): head-on 36-38% vs circular
   23-24% (MONOTONIC). The replay is BIASED toward W=0.0 (enemy path was reactive
   to our actual circular shots) yet head-on STILL wins by ~13 points -> very
   strong signal (bias would favor circular, not against). This is a MILD-curve
   mover (dh 0.035), so head-on is correct; circular was wrong (that was for the
   heavy-spinner spinbot).
2. MOVEMENT orbit ~250px -> ~185-200px (conservative hedge). rangeBias: >400 -> -0.9,
   >290 -> -0.6, >210 -> -0.3, <160 -> +0.45 (push out of deadly <150px zone).
   Targets the 100-200px net-positive zone (66% hit) instead of the -6254 zone.
Power tiers (3.0/<300 2.4/<400 1.6/<550), fire gates, energy-war taper UNCHANGED.
Compiles Java 8 (major version 52), rc=0. Backup of prior source: /tmp/MyTank.bak.java.

## For next teammate — VERIFY
- Want NEW /logs win rate ABOVE 94% (ideally 100%), the 15 losses GONE, our final
  E mean UP from 59.4, killtick DOWN from 450, enemy share DOWN from 16%.
- If it REGRESSED: (a) if new losses appear, the closer orbit may have exposed us
  to the enemy's close gun -> push orbit back to ~210px (thresholds 450/320/230/<190)
  or revert movement only; (b) if hit rate dropped, unlikely (W-sweep clean) but
  could try W=0.5 half-lead. Full revert = /tmp/MyTank.bak.java (git prior, 94%).
- npcsniper is a MODERATE mild-curve mover -> KEEP W=1.0 head-on unless profile
  changes. If it becomes a HEAVY spinner (avg|dh|>0.06), set W=0.0 (circular).
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the opponent name first.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = iagomonteiro13579__npcsniper (COMPETITIVE, 15 losses)

## STATUS: Round 1's head-on+closer-orbit change did NOT fix the 15 losses.
Verified /logs/rounds/{0,1} (INDEX MAPPING THIS ROUND: i=0=opus, i=1=npcsniper —
opposite of round-1 notes; always read the header). Round 0: opus 39031 vs 7379.
Round 1: opus 39792 vs 7653. Enemy still ~16% share. Full 250-sim sweep round 1:
LOSSES=15/250, close(<20E)=26, our final E mean 63.6 (min 0.0), killtick 393,
turns mean 562 (max 1228). Same as round 0 — the R1 W=1.0/orbit-185 change was neutral.

## OPPONENT PROFILE: STOP-AND-GO DODGER (bimodal velocity) that CONSERVES energy
movefrac 0.75, avgV 4.39, avg|dh| 0.031 (MILD curve), engages ~279px. Velocity
is BIMODAL: v=0 ~24% of ticks, v=8 ~32% (stops then sprints). Fires ~half as
often as us (16-43 vs our 40-66 shots/game). Its gun is decent.

## ROOT CAUSE of the 15 losses: ENERGY-WAR BLEED at 200-400px
MEASURED hit rate + net firing energy by distance (round-1 250 sims, DECISIVE):
  0-100px:   hr 75%, enemyhit 15.6/1k, NET +16/1k  (deadly enemy gun — avoid)
  100-200px: hr 49%, enemyhit  6.0/1k, NET +13/1k  (ONLY sustainable WIN zone)
  200-300px: hr 26%, enemyhit  5.3/1k, NET -77/1k  (we spent 55k ticks HERE bleeding!)
  300-400px: hr 17%, enemyhit  8.1/1k, NET -131/1k (catastrophic)
  400-600px: hr 16-19%, NET -30..-56/1k
We engaged ~279px (mid of the losing zone) firing FLAT power 3.0/<300 at 26% hit
-> lost the energy war -> in losses we fired 40-66 shots (5-13 hits) to enemy's
16-43 and BLED to 0 while enemy kept 2-61 E. Pure grind-loss (both conserve, our
per-miss drain kills us first).

## CHANGES THIS PASS (BOTH attack the energy-war bleed; gun aim UNCHANGED)
Gun aim W=1.0 head-on CONFIRMED correct via fresh W-sweep 2 slices (monotonic to
head-on ~40% vs circular ~25%) — NOT the problem. The problem is power + distance.
1. POWER TIERS: was flat 3.0/<300, 2.4/<400, 1.6/<550, 1.0/else. NOW 3.0/<200,
   1.6/<300, 1.0/<400, 0.6/else. Keeps full power ONLY in the <200px net-positive
   zone; tapers HARD beyond so each far miss barely costs energy. Net-firing-energy
   model over 250 recorded games (MEASURED hit rates): OLD -1773 -> NEW +398.
2. MOVEMENT rangeBias: strengthened inward pull to actually reach <200px (was
   maxing at -0.9 but enemy kept distance open -> we stayed at 279px). NOW
   >350->-1.2 (nearly head-on toward enemy to close), >250->-0.9, >180->-0.5,
   <130->+0.5 (push out of the 0-100px deadly zone). Target orbit ~160px.
Net/tick model: 100-200px +0.013 vs 200-300px -0.061 -> shifting inward flips us
from bleeding to gaining. Both changes compile Java 8 (major version 52).
Backup of prior source: /tmp/MyTank.bak.java.

## Tool: /tmp/wsweep.py (W-sweep) rebuild from this. The DECISIVE tool this round
was the net-firing-energy-by-distance model (MEASURED per-bucket hit rates), not
the biased replay hit-rate sim. One-liner: bucket by dist at prev tick; our energy
drops in (-3.1,-0.05)=fire, gains>0.1=hit, drops<-3.5=enemy hit us; NET/tick =
fire_rate*(hr*3*power-power) - enemyhit_rate*8.

## For next teammate — VERIFY
- Want NEW /logs win rate ABOVE the current ~94% (ideally 100%), the 15 losses
  GONE, our final E mean UP from 63.6, enemy share DOWN from 16%, engagement dist
  DOWN from 279 toward ~180px.
- IF IT REGRESSED (new losses / share drop): (a) the closer orbit may have exposed
  us to the enemy's close gun (0-100px is deadly 15.6/1k) -> if we overshoot below
  130px too often, raise the push-out threshold (<160->+0.5) or soften the far pull
  to -1.0; (b) if the power taper made kills too slow (enemy survives to turn limit
  with high E), raise the 200-300px tier back toward 2.0. Full revert =
  /tmp/MyTank.bak.java (git prior, the R1 config = 94%/15 losses).
- npcsniper is a stop-and-go MILD-curve mover -> KEEP W=1.0 head-on. If it becomes
  a HEAVY spinner (avg|dh|>0.06), set W=0.0 (circular). Always re-check
  `head -1 /logs/rounds/0/sim_0.jsonl` for the opponent name + INDEX MAPPING first.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = gabriel_lw__quadwall

## STATUS: PERFECT WIN (250/250), 92% share — NO CODE CHANGE (data-optimal)
Opponent CHANGED to gabriel_lw__quadwall. Verified /logs/rounds/0:
- results.json: opus-4-8 45262 vs gabriel_lw__quadwall 5095.
- results_0.txt: opus_4_8.MyTank 1787 (92%), 10/10 firsts; enemy 152 (8%).

## Opponent = SLOW, NEAR-STATIONARY mover (barely moves, near-straight)
Per-sim analysis (250 games, header maps idx->name, enemy=non-'opus'; i=0=quadwall):
- movefrac 0.248, avg|v| 1.32 (SLOW), avg|dh| 0.016 (near-straight, minimal turn),
  engages ~248px. Loses the energy war decisively.
- Full 250-sim sweep: LOSSES = 0/250, close(<20E) = 0/250. Our final energy
  min/mean = 57.6/110.8 (large margin). Mean killtick 239, turns mean 390 (max 721).
  Enemy DIES every game.

## Gun aim W=1.0 head-on CONFIRMED near-optimal (fresh W-sweep, 80 games)
Per-tick interception over recorded paths, power 3.0:
  W=0.0 69.3% | W=0.25 70.6% | W=0.5 71.9% | W=0.75 73.7% | W=1.0 72.6%.
W=0.75 marginally higher than W=1.0 (73.7 vs 72.6, within noise) but for a slow
near-stationary target head-on is theoretically correct and matches all prior
slow-mover findings. The ~1pt diff is not worth touching a 250/250 config.

## Decision: NO code change (deliberate)
Gun was already at W=1.0 head-on (from npcsniper round). It's data-optimal for this
slow near-stationary target. The ~8% leak is unavoidable enemy survival-bullet
damage during the ~239 ticks before the kill. Raising power to kill faster REGRESSES
(longer cooldown -> longer engagement -> MORE enemy hits, documented repeatedly).
Any edit only risks regression on a 250/250 sweep we win with 57+ E to spare.
Re-verified compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
quadwall is SLOW/near-stationary -> KEEP W=1.0 head-on. If it becomes a FAST curving
dodger (avg|v|>4, movefrac>0.7, avg|dh|>0.06), set W=0.0 (circular) + orbit out ~260px.
Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the current opponent name first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass, this pass) — opponent = gabriel_lw__quadwall

## STATUS: PERFECT WIN both rounds — NO CODE CHANGE (deliberate)
Verified /logs/rounds/{0,1} (opponent gabriel_lw__quadwall, SLOW near-stationary
mover: movefrac 0.248, avg|v| 1.32, avg|dh| 0.016 near-straight, engages ~248px):
- Round 0: opus 45262 vs quadwall 5095 (92% share), 10/10 firsts. Full 250-sim
  sweep: LOSSES=0/250, close(<20E)=0. ourFE min/mean 57.6/110.8.
- Round 1: opus 45125 vs quadwall 4652 (90% share, results_0.txt), 10/10 firsts.
  Full 250-sim sweep: LOSSES=0/250, close(<20E)=0. ourFE min/mean 34.2/112.8,
  mean killtick 234, turns max 755. Enemy DIES every game.

## Decision: NO code change
Gun = W=1.0 head-on (line 294), data-optimal for this slow near-stationary target
(any lead overshoots). git diff on MyTank.java = empty (unchanged winning config).
The ~8-10% leak is unavoidable enemy survival-bullet damage during the ~234 ticks
before the kill. Raising power to kill faster REGRESSES real games (longer cooldown
-> longer engagement -> MORE enemy hits — documented repeatedly). Any edit only
risks regression on a 250/250 sweep we win with 34+ E to spare.
Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
quadwall is SLOW/near-stationary -> KEEP W=1.0 head-on. If it becomes a FAST
curving dodger (avg|v|>4, movefrac>0.7, avg|dh|>0.06), set W=0.0 (circular) +
orbit out ~260px. Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the
current opponent name + INDEX MAPPING first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 1 / current pass) — opponent = zcjerry229__markrobo

## STATUS: 249/250 win, 89% share — NO CODE CHANGE (data-optimal, deliberate)
Opponent CHANGED to zcjerry229__markrobo. Verified /logs/rounds/0:
- results.json: opus-4-8 44491 vs zcjerry229__markrobo 5169.
- results_0.txt: opus_4_8.MyTank 1770 (89%), 10/10 firsts; enemy 214 (11%).

## Opponent = SLOW near-STRAIGHT-LINE mover (index i=1 this round; read header!)
Per-sim analysis (250 games, header maps idx->name, enemy=non-'opus'):
- movefrac 0.40, avg|v| 1.79 (SLOW), avg|dh| 0.011 (near-straight), engages ~226px.
  Loses the energy war (fires ~half as often as us: 18 vs 34 in grinds, 7 vs 18 typ).
- Full 250-sim sweep: LOSSES = 1/250 (sim_128), close(<20E) = 4. Our final energy
  min/mean = 0.0/95.2. Mean killtick 319, turns mean 470 (max 882).

## The 1 loss (sim_128) = 803-turn energy-war GRIND (variance, not fixable cheaply)
Behind on energy 96% of ticks; spent 144 ticks @200-300px + 103 @300-400px (losing
zones). We fired ~2x the enemy and bled to 0 (enemy kept 82E). Classic grind loss
vs an energy-conserving mover. Modeled tapering power harder when behind (taper
dist>300 -> >250 or >200) over the 4 grind/close games: net firing energy changed
<0.2% (at 200-300px we already fire power 1.6 at 33% real hit = ~break-even, so the
taper has nothing to bite). No cheap power/taper fix converts this variance loss.

## Movement is already data-optimal — tested tighter orbit, REVERTED
Distance histogram: we spend 48.8% of ticks @100-200px (BEST zone: 57% hit,
enemy density only 3.4/1k), 32% @200-350px (marginal). Measured hit/density by dist:
  0-100px hr 0.67, enemy 0.0/1k | 100-200px hr 0.57, 3.4/1k (dominant win zone)
  200-300px hr 0.33, 7.0/1k (enemy's DEADLIEST zone) | 300-400px hr 0.23, 3.4/1k
Considered tightening rangeBias (250->220, 180->160, push-out 130->120) to shift
the 22.7% at 200-250px inward, but REVERTED: movement is GLOBAL across the ladder
and closer orbit was DEADLY vs the fast-curving team488__meow (14.8 enemy hits/1k
@100-200px, that teammate orbited FAR ~260px). A ~15px tighten to shave 1 variance
loss vs a slow-gunned foe risks regressing strong close-range gunners elsewhere.

## Gun aim W=1.0 head-on CONFIRMED near-optimal (fresh W-sweep, 80 games)
Per-tick interception over recorded paths: W=0.0 47.5% | W=0.5 53.9% | W=0.75 56.3%
| W=1.0 53.0%. W=0.75 marginally beats W=1.0 (~3pts) BUT the replay is BIASED toward
W=1.0 (enemy path reactive to our actual head-on shots) so W=0.75's tiny edge is
noise. For a slow near-straight mover head-on is theoretically correct and won
249/250. Not worth touching (documented trap: don't switch aim off biased replay).

## Decision: NO code change (deliberate)
git diff on MyTank.java = empty. W=1.0 head-on (line 294), power tiers 3.0/<200
1.6/<300 1.0/<400 0.6/else, orbit ~160px w/ graduated inward pull (-1.2/-0.9/-0.5,
push-out <130), energy-war taper (behind & dist>300 -> power<=0.8), no-fire gates
(dist>400/550 & behind), low-E clamps. Data-optimal for this slow energy-losing
mover. The 11% leak is unavoidable enemy survival-bullet damage over ~319 ticks
before the kill. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows a MATCH loss (enemy wins a 10-round battle) or win
rate collapsing. markrobo is SLOW/near-straight & conserves energy -> KEEP W=1.0
head-on. If grind losses RISE to a match threat, the targeted lever is the
far-range fire gate (allow tiny power ~0.1 far shots when behind to contest the
free-damage concession) — but validate vs the LADDER, not just markrobo. If it
becomes a FAST curving dodger (avg|v|>4, avg|dh|>0.06), set W=0.0 (circular) +
orbit out ~260px. Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the
current opponent name + INDEX MAPPING first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 verification pass, THIS pass) — opponent = zcjerry229__markrobo

## STATUS: 249/250 win both rounds — NO CODE CHANGE (deliberate, re-verified)
Verified /logs/rounds/{0,1} (opponent zcjerry229__markrobo, SLOW near-straight
mover; INDEX MAPPING FLIPS per round — R0 opus=i0, R1 opus=i1, always read header):
- Round 0: opus 44491 vs markrobo 5169. 10/10 firsts.
- Round 1: opus 44546 vs markrobo 4728. 10/10 firsts. Full 250-sim sweep:
  LOSSES=1/250 (sim_201), close(<20E)=1. ourFE min/mean 0.0/98.3. killtick
  mean 306.8 (median 296.5), turns mean 458 (max 774, only 13 games >600t).

## The 1 loss (sim_201) = 775-turn energy-war GRIND (variance, not fixable cheaply)
Behind on energy 43% of ticks; spent 43% of ticks @200-300px (enemy's DEADLIEST
zone) + 26% @300px+ (net-negative). We couldn't close to the <200px win zone in
that grind. Same profile as R0's sim_128 loss. We win 10/10 firsts EVERY battle
so this is NOT a match-level loss — pure variance.

## Measured hit rate + enemy density by distance (150 round-1 games) — DECISIVE
  0-100px:   hr 0.74, enemy 0.0/1k
  100-200px: hr 0.57, enemy 3.2/1k  (DOMINANT win zone — we spend 33k ticks here)
  200-300px: hr 0.38, enemy 6.9/1k  (enemy's deadliest; still net-pos but risky)
  300-400px: hr 0.27, enemy 3.7/1k  (net-negative)
Current movement (orbit ~160px, graduated inward pull -1.2/-0.9/-0.5, push-out
<130) correctly camps the 100-200px zone. Power tier at <200px = full 3.0 (fast
kills in the win zone). Both data-optimal.

## Why NO change (followed proven discipline)
Gun W=1.0 head-on is optimal for this slow near-straight mover (any lead over-
shoots; documented across all slow-mover matches). Movement is GLOBAL across the
ladder — README repeatedly documents closer orbit was DEADLY vs fast-curving
foes (team488__meow 14.8 enemy hits/1k @100-200px). Shaving 1 variance loss vs a
slow-gunned foe by tightening orbit risks regressing strong close-range gunners.
The ~10% score leak is unavoidable enemy survival-bullet damage over ~306 ticks
before the kill; raising power to kill faster REGRESSES (longer cooldown -> longer
engagement -> more enemy hits — documented repeatedly).

## Compile: git diff on MyTank.java = empty. Re-verified:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows a MATCH loss (enemy wins a 10-round battle) or win
rate collapsing. markrobo is SLOW/near-straight & conserves energy -> KEEP W=1.0
head-on. If grind losses RISE to a match threat, the targeted lever is the
far-range fire gate (allow tiny power ~0.1 far shots when behind to contest the
free-damage concession) — validate vs the LADDER, not just markrobo. Always
re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the opponent name + INDEX
MAPPING first. Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = dankraemer__juggernaut (COMPETITIVE, 16 losses)

## KEY FINDING: juggernaut has a GOOD LEAD-AIMING gun — losses are a DODGING problem
Round 0 result (BEFORE my change): opus-4-8 42256 vs dankraemer__juggernaut 10269
(21% share — highest enemy share in a while). results_0.txt: opus 1648 (79%),
9/10 firsts (enemy got 1 first!). trace.md WIN RATE 94% (234/250) — 16 LOSSES.
Our accuracy 46%, ENEMY accuracy 29% overall (but 45% in the games it WINS).

## Opponent profile (120 sims; header maps idx->name, enemy=non-'opus')
- movefrac 0.56, avg|v| 3.32 (moderate), avg|dh| 0.0586 (moderate curve, near
  the 0.06 heavy-spinner threshold), engages CLOSE (dist mean 229 / median 205).

## Gun aim W=1.0 head-on CONFIRMED best (my replay-sim, 2 slices, 80 games each)
Per-tick interception over recorded paths: head-on 55.5/53.6% vs linear 48.6/49.8%
vs CIRCULAR 46.4/47.4%. Head-on wins clearly DESPITE the moderate curve (enemy is
reactive/stop-and-go enough that head-on beats lead). KEPT W=1.0 (do NOT switch to
circular off the curve alone — the replay is decisive here).

## ROOT CAUSE of the 16 losses: enemy out-trades us because ITS gun is hot
LOSSES: we fire 353 shots / hit 88 (25%); enemy fires 181 / hits 81 (45%!).
WINS:   we fire 280 / hit 134 (48%); enemy fires 117 / hits 37 (32%).
Losses are games where the enemy's LEAD gun connects (45%) and ours cools (25%).
Behind on energy 77% of ticks in losses vs 23% in wins. NOT a distance problem
(losses avg 217px vs wins 210px) and NOT wall/speed (losses have LESS wall time,
HIGHER speed). It's the enemy's gun accuracy -> a MOVEMENT/dodging problem.

## MEASURED enemy uses LEAD (predictive) targeting — the key to the fix
When the enemy fires, its gun points ~0.364 rad OFF head-on (it aims where we
WILL be). Against a lead-aiming gun the strongest evasion is to REVERSE on its
fire: its lead shot flies to the far side and misses.

## Full net energy by distance (measured, 150 games) — 100-200px is the ONLY zone
  0-100px:   FULLNET -40/1k (enemy gun deadly close)
  100-200px: FULLNET  -1/1k (BEST — near break-even; we camp here, orbit ~160px)
  200-300px: FULLNET -69/1k (catastrophic: our hit drops to 33%, enemy still hot)
  300-400px: FULLNET -51/1k | 400-500px -16/1k
Current orbit (~160px, graduated inward pull) already camps the right zone; the
drift to 217px in losses is a SYMPTOM of enemy hits, not a config bug. Movement
distance UNCHANGED (closer = enemy's 0-100px deadly zone; wider = losing zones).

## CHANGES THIS PASS (movement dodging only — gun/distance UNCHANGED)
1. Dodge-on-enemy-fire probability 0.45 -> 0.70 (reverse to dodge the lead shot).
2. Dodge rate-limit 6 -> 5 ticks (dodge consecutive waves).
3. onHitByBullet reverse 0.5 -> 0.8 (a hit means we were profiled -> disrupt harder).
Rationale: directly attacks the 45% enemy hit rate in losses. Kept below a strict
alternation (0.70, not 1.0) so it's not itself learnable, plus rare random reversal
(0.08) to break residual period. Backup of prior source: /tmp/MyTank.bak.java.
Compiles Java 8 (major version 52), rc=0.

## For next teammate — VERIFY
- Want NEW /logs win rate ABOVE 94% (ideally 97%+), enemy accuracy DOWN from 45%
  in-loss / 29% overall, enemy score DOWN from 10269, fewer/no losses, our avg
  min-E UP from 61. If it REGRESSED (new losses / share drop), the higher dodge
  rate may have made us MORE learnable (a fixed ~70% dodge cadence) or cut our own
  lateral coverage -> lower dodge back to 0.55-0.60 or revert to /tmp/MyTank.bak.java
  (git prior, 94%). If it worked, could push dodge to 0.75.
- juggernaut is a MODERATE curving mover with a LEAD gun -> KEEP W=1.0 head-on
  (replay-confirmed) and the dodge-on-fire evasion. The remaining lever if losses
  persist is WAVE SURFING (track enemy bullet waves, move to min-danger GF) — the
  only robust anti-lead-gun movement, but high-risk; local harness broken, trust
  /logs only. Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the opponent
  name + INDEX MAPPING first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 / current pass) — opponent = dankraemer__juggernaut — REVERTED ROUND-1 DODGE REGRESSION

## CRITICAL: Round 1's aggressive-dodge change REGRESSED — reverted to Round-0 config
This opponent = dankraemer__juggernaut, a MODERATE curving mover (movefrac 0.56,
avgV 3.32, avg|dh| 0.059) with a GOOD LEAD-AIMING gun (points ~0.36 rad off head-on
when it fires). Genuinely competitive — our highest enemy-share foe in a while.

## REAL cross-round results (the decisive signal):
- ROUND 0 (dodge-on-fire 0.45, onHitByBullet 0.5, periodic 0.07):
  opus 42256 vs juggernaut 10269. results_0.txt: opus 1648 (79%), 9/10 firsts.
  Full 250-sim sweep: 15 LOSSES, ourFE mean 78.7.
- ROUND 1 (prior teammate RAISED dodge-on-fire 0.45->0.70, onHitByBullet 0.5->0.8,
  periodic 0.07->0.08): opus 41914 vs juggernaut 13195. results_0.txt: 1727 (76%),
  9/10 firsts. Full 250-sim sweep: 26 LOSSES (worse!), ourFE mean 69.9.
  The "dodge MORE on enemy fire" theory BACKFIRED — enemy score went UP 10269->
  13195, losses 15->26. Forced reversals at the enemy's fire cadence reduced our
  lateral coverage / became learnable, exposing us more.

## THIS PASS: reverted dodge params to the Round-0 (better) config
Restored dodge-on-fire 0.45 (>=6 tick gate), onHitByBullet reverse 0.5, periodic
reversal 0.07. Verified FUNCTIONALLY IDENTICAL to git 7c1b825 (the round-0 winning
config) via `diff` ignoring comments. Gun (W=1.0 head-on), power tiers (3.0/<200
1.6/<300 1.0/<400 0.6/else), orbit ~160px, energy-war taper, fire gates UNCHANGED.
Compiles Java 8 (major version 52). rc=0.

## ROOT-CAUSE analysis of the 15 losses (round 0): energy-war VARIANCE, not position
- Losses vs wins have SAME distance dist (~43% at 100-200px both). NOT positional.
- The ONLY difference: "behind on energy" 79% of ticks in losses vs 16% in wins.
  Pure hit-rate variance in the energy war (enemy's lead gun connects ~45% in
  losses vs ~32% in wins; ours cools 25% vs 48%).
- Measured hit/enemy-density by distance (150 games): 0-100px hr0.78 enemy13.1/1k,
  100-200px hr0.60 enemy6.5/1k (BEST net zone — we camp here), 200-300px hr0.33
  enemy4.3/1k (break-even). Current orbit (~160px) already camps the right zone.
- Modeled extending the energy-war taper to 200px: net change +0.1% (negligible —
  200-300px hr 33% is already ~break-even, taper has nothing to bite). NOT worth it.

## Interesting (unused) signal: losses had LOWER reversal rate (0.0076/tick) than
## wins (0.0107/tick) — we were STEADIER (more predictable) in loss games. This
## suggests MORE *uncorrelated* unpredictability (periodic random reversal, NOT
## fire-triggered) MIGHT help. I considered bumping periodic reversal 0.07->0.10
## but REVERTED it: can't validate (local harness broken), and it risks lowering
## our own hit rate (more lateral direction changes -> gun realigns). The proven
## round-0 config is the safe, better-than-round-1 choice. If a next teammate wants
## to try it, bump ONLY the periodic reversal (line 446, 0.07->0.10-0.12), NOT the
## fire-triggered dodge (round-1 proved raising that REGRESSES). Weight the REAL
## /logs win rate far above any replay-sim.

## Aim W-sweep (round-0 replay, 80 games, BIASED toward W=1.0): W=1.0 57.9%,
## W=0.75 59.1% (marginal), W=0.5 56.2%. W=0.75 barely edges W=1.0 but within
## noise + biased. Do NOT switch aim off this. Keep W=1.0 head-on (proven).

## For next teammate
- Only act if NEW /logs shows losses RISING above 15 or a MATCH loss. juggernaut
  is a MODERATE lead-gun mover -> KEEP W=1.0 head-on and the round-0 dodge config
  (0.45 fire-dodge). Do NOT raise fire-triggered dodge (round-1 proved -REGRESSION).
- The only robust remaining lever vs its 45%-in-loss lead gun is WAVE SURFING
  (track enemy bullet waves, move to min-danger GF) — high-risk, local harness
  broken, trust /logs only. A safer micro-experiment: raise ONLY periodic
  (uncorrelated) reversal 0.07->0.10.
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = robo_code__trackfire

## STATUS: PERFECT WIN (250/250), 88% share — NO CODE CHANGE (data-optimal)
Opponent CHANGED to robo_code__trackfire (the "TrackFire" sample bot: STATIONARY,
tracks + fires but never moves). Verified /logs/rounds/0:
- results.json: opus-4-8 44400 vs robo_code__trackfire 5548.
- results_0.txt: opus_4_8.MyTank 1800 (88%), 10/10 firsts; enemy 252 (12%).

## Opponent = STATIONARY (index i=1 this round; read the header!)
Per-sim analysis (250 games, header maps idx->name, enemy=non-'opus'):
- movefrac 0.0, avg|v| 0.0, avg|dh| 0.0 (NEVER moves — pure sitting duck that
  rotates its gun & fires). Engages ~235px. Fires back enough to leak ~12% share.
- Full 250-sim sweep: LOSSES = 0/250, close(<20E) = 1 (sim_35, ourFE 0.7 but we
  were behind on energy only 4% of ticks -> a game we dominated the energy war but
  ended low = pure variance, still a WIN). Our final energy min/mean = 0.7/114.7.
  Mean killtick 161.5. Enemy DIES every game.

## Gun aim W=1.0 head-on is data-optimal for a STATIONARY target (any lead overshoots)
Current gun (W=1.0 head-on, power tiers 3.0/<200 1.6/<300 1.0/<400 0.6/else, orbit
~160px w/ graduated inward pull, energy-war taper, fire gates, low-E clamps) is
correct. The ~12% leak is unavoidable enemy survival-bullet damage during the ~161
ticks before the kill. Raising power to kill faster REGRESSES real games (longer
cooldown -> longer engagement -> MORE enemy hits — documented repeatedly across
myfirstkiller/exterminador/tracker/crazy). Any edit only risks regression on a
250/250 sweep we win with margin to spare.

## Decision: NO code change (deliberate)
git diff on MyTank.java = empty (unchanged winning config). Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss.
trackfire is STATIONARY -> KEEP W=1.0 head-on. If it becomes a FAST curving dodger
(avg|v|>4, movefrac>0.7, avg|dh|>0.06), set W=0.0 (circular) + orbit out ~260px.
Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the opponent name + INDEX
MAPPING first. Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass, THIS pass) — opponent = robo_code__trackfire

## STATUS: 249/250 sim win both rounds, 10/10 firsts EVERY battle — NO CODE CHANGE
Opponent = robo_code__trackfire (the "TrackFire" sample bot: STATIONARY, rotates
gun + fires but NEVER moves). INDEX MAPPING both rounds: i=0=opus, i=1=trackfire.
- Round 0: opus 44400 vs trackfire 5548. results_0.txt: 1800 (88%), 10/10 firsts.
- Round 1: opus 44360 vs trackfire 6373. results_0.txt: 1824 (85%), 10/10 firsts.
- Full 250-sim sweep round 1: LOSSES=1/250 (sim variance — a game we dominated the
  energy war, behind on E only 4% of ticks, but ended at 0 E = coin-flip), close
  (<20E)=1. ourFE min/mean 0.0/112.5. mean killtick 169.7. Enemy DIES every game.

## Decision: NO code change (deliberate)
Gun = W=1.0 head-on (line 294), DATA-OPTIMAL for a STATIONARY target (any lead
overshoots). git diff on MyTank.java = empty. Compiles Java 8 (major version 52).
The ~12-15% score leak is unavoidable enemy survival-bullet damage during the ~170
ticks before the kill. Raising power to kill faster REGRESSES real games (longer
cooldown -> longer engagement -> MORE enemy hits — documented repeatedly across
myfirstkiller/exterminador/tracker/crazy). The 1 sim loss is pure variance, NOT a
match-level loss (we win 10/10 firsts in every 10-round battle). Any edit only
risks regression on a sweep we already win with margin.
Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows a MATCH loss (enemy wins a 10-round battle) or win
rate collapsing. trackfire is STATIONARY -> KEEP W=1.0 head-on. If it becomes a
FAST curving dodger (avg|v|>4, movefrac>0.7, avg|dh|>0.06), set W=0.0 (circular) +
orbit out ~260px. Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the
opponent name + INDEX MAPPING first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 1 / current pass) — opponent = joaomcarvalho__jeujdapeu (COMPETITIVE, 30 losses)

## KEY FINDING: jeujdapeu is a MODERATE curving mover with a STRONG LEAD gun — we camped in its kill zone
Round 0 result (BEFORE my change): opus-4-8 38946 vs joaomcarvalho__jeujdapeu 14625
(27% share — the HIGHEST enemy share we've faced). results_0.txt: opus 1470 (70%),
8/10 firsts (enemy got 2 firsts!). trace.md WIN RATE 88% (220/250) — 30 LOSSES.
Our accuracy 38%, enemy 27% overall.

## Opponent profile (120 sims; header maps idx->name, enemy=non-'opus')
- movefrac 0.75, avg|v| 3.56 (moderate), avg|dh| 0.055 (MODERATE curve, near the
  0.06 heavy-spinner threshold), engages CLOSE (dist mean 220 / median 193).
- MEASURED enemy gun offset when firing: median 0.605 rad, mean 0.779 rad off
  head-on -> it uses a STRONG LEAD (predictive) gun, aims where we WILL be.

## ROOT CAUSE of the 30 losses: enemy's lead gun connects at CLOSE range
LOSSES vs WINS (energy-delta analysis, 250 games):
- LOSSES: enemy accuracy 35%, fires 17.3/hits 6.0; we fire 23.8/hit 7.1 (30%).
  Behind on energy 72% of ticks. WINS: enemy 22% acc (12.1 fires/2.7 hits), we
  40% (18.9/7.6), behind only 13%. Distance IDENTICAL (219px both) -> NOT positional
  drift; it's the enemy's lead gun connecting + energy-war bleed.
- MEASURED enemy hit density by distance (150 games): 100-200px 9.7/1k (DEADLIEST,
  where we camped ~160px), 200-300px 5.4/1k, 300-400px only 1.8/1k, 400+ <0.8/1k.
- OUR head-on hit rate by distance (replay-sim, 80 games) BARELY drops with range:
  100-200px 50%, 200-300px 43%, 300-400px 44%, 400-500px 35%.
=> We were orbiting RIGHT IN the enemy's kill zone with no accuracy benefit.

## CHANGES THIS PASS (movement + power; gun aim UNCHANGED at W=1.0)
1. MOVEMENT: orbit ~160px -> ~280px. rangeBias: >480 -> -1.0, >350 -> -0.6,
   >290 -> -0.3, <240 -> +0.5 (push out of the lead-gun kill zone). At 280px our
   hit rate holds ~43% while enemy hits drop from 9.7/1k to ~2-5/1k (~half or less).
   Same insight that beat the lead-gun/curving foes spinbot (~250px) and
   team488__meow (~260px, 93%->100%).
2. POWER: was 3.0/<200, 1.6/<300, 1.0/<400, 0.6/else (tuned for close npcsniper).
   NOW 3.0/<300, 1.6/<400, 1.0/<500, 0.6/else. Keeps full power in the new
   200-300px camp zone where our HR is 43% (net-positive) and we WIN the energy
   war (88% wins). Taper beyond 300px where hit rate falls.
Gun aim W=1.0 head-on CONFIRMED best via W-sweep 2 slices (monotonic to head-on
47.6%/46.0% vs W=0.0 33.8%/33.2%; jeujdapeu is a MODERATE curver, not a heavy
spinner, so head-on beats circular). Fire gates, energy-war taper, dodge-on-fire
(0.45) all UNCHANGED. Compiles Java 8 (major version 52). Backup: /tmp/MyTank.bak.java.

## Tool: /tmp/wsweep.py (W-sweep), /tmp/dist.py (head-on HR by distance). Rebuild
from these notes if lost. The DECISIVE unbiased tool was enemy-hit-density-by-
distance (enemy's ACTUAL recorded hits on us, not reactive to our aim): 100-200px
9.7/1k vs 300-400px 1.8/1k made the orbit-wider call clear.

## For next teammate — VERIFY
- Want NEW /logs win rate ABOVE 88% (ideally 97%+), the 30 losses reduced, enemy
  score DOWN from 14625, enemy accuracy DOWN from 35% in-loss, our avg min-E UP
  from 47. If it REGRESSED (new losses / share drop): the wider orbit may have
  cut our own hit rate more than expected OR the enemy's lead gun may actually be
  good at 280px too -> pull orbit back to ~230px (thresholds 430/300/240/<200) or
  revert to /tmp/MyTank.bak.java (git prior, 88%/close-orbit). If it worked, could
  push orbit to ~320px (thresholds 500/380/330/<280) since our HR holds ~44% at
  300-400px and enemy hits are only 1.8/1k there.
- jeujdapeu is a MODERATE lead-gun curver -> KEEP W=1.0 head-on. If it becomes a
  HEAVY spinner (avg|dh|>0.07), set W=0.0 (circular). If a fast straight mover,
  lower W toward 0.25. Re-run /tmp/wsweep.py on >=2 slices first.
- The remaining lever if losses persist is WAVE SURFING (its lead gun is 35%
  accurate in losses -> dodging its bullet waves is the biggest untapped win, but
  high-risk; local harness broken, trust /logs only).
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = joaomcarvalho__jeujdapeu

## STATUS: R1's orbit-wider change WORKED (losses 29->14). THIS pass: orbit CLOSER (~245px).
Verified /logs/rounds/{0,1} (opponent joaomcarvalho__jeujdapeu, MODERATE curving
mover with a STRONG LEAD gun; INDEX FLIPS per round — R0 opus=i1, R1 opus=i0):
- R0 (orbit ~160px): opus 38946 vs 14625 (70% share, 8/10 firsts). 29 losses/250.
- R1 (prior teammate orbit ~280px): opus 36368 vs 9208. results_0.txt 1326 (71%),
  8/10 firsts. Full 250-sim sweep: LOSSES=14/250 (down from 29!), close(<20E)=29,
  ourFE mean 59.7, killtick 377. The orbit-wider change HALVED the losses. GOOD.

## ROOT CAUSE of remaining 14 losses: net-negative firing at 300-400px
Measured hit rate + enemy density by distance (150 R1 games, DECISIVE unbiased):
  100-200px: ourHR 0.40, enemy 3.4/1k
  200-300px: ourHR 0.35, enemy 5.0/1k  (37538 ticks — most time, best HR zone)
  300-400px: ourHR 0.26, enemy 4.6/1k  (28111 ticks — net-NEGATIVE firing here!)
  400-500px: ourHR 0.21, enemy 2.1/1k
We actually engaged at ~314px avg (drifted WIDE of the ~280px target due to enemy
motion + wall smoothing), spending 28k ticks at 300-400px bleeding energy. Enemy
hit density is NEARLY THE SAME at 200-300 (5.0) vs 300-400 (4.6), so closing to
~245px loses ~nothing on defense but RAISES our HR 0.26->0.35.

## CHANGE THIS PASS (movement only): orbit ~280px -> ~245px (stronger inward pull)
rangeBias thresholds: was >480/-1.0, >350/-0.6, >290/-0.3, <240/+0.5 (target ~280).
NOW >450/-1.1, >330/-0.7, >260/-0.35, <210/+0.5 (target ~245px). Net-energy model
(measured per-bucket HR + density): OLD -71.7/1k -> NEW ~-22/1k (70% less bleed).
Gun aim W=1.0 head-on CONFIRMED optimal (fresh W-sweep round-1, monotonic to
head-on 0.369 vs 0.201 full-lead — jeujdapeu is a moderate curver, head-on beats
any lead). Power tiers (3.0/<300, 1.6/<400, 1.0/<500, 0.6/else), dodge, energy-war
taper, fire gates UNCHANGED. Compiles Java 8 (major version 52). Backup: /tmp/MyTank.bak.java.

## For next teammate — VERIFY
- Want NEW /logs losses BELOW 14 (ideally <8), enemy share DOWN from 29%, ourFE
  mean UP from 59.7, engagement dist DOWN from ~314 toward ~245px. If it REGRESSED
  (new losses / share drop), the closer orbit may have exposed us to the enemy's
  lead gun at close range -> push orbit back toward ~270px (thresholds 470/350/280/
  <230) or revert to /tmp/MyTank.bak.java (git prior, 14 losses). If it worked but
  losses persist, the remaining lever is WAVE SURFING (enemy lead gun ~35% in
  losses -> dodging its waves is the biggest untapped win, high-risk; harness broken).
- jeujdapeu is MODERATE lead-gun curver -> KEEP W=1.0 head-on. If it becomes a
  HEAVY spinner (avg|dh|>0.07), set W=0.0 (circular). Always re-check
  `head -1 /logs/rounds/0/sim_0.jsonl` for opponent name + INDEX MAPPING first.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = it_economics__ite_m9 (COMPETITIVE, 22 losses)

## KEY FINDING: gun aim W was too high (W=1.0 head-on) for this SLOW mover with a tiny curve
Round 0 result (BEFORE my change): opus-4-8 39510 vs it_economics__ite_m9 7909
(17% share). results_0.txt: opus 1675 (84%), 10/10 firsts BUT trace.md WIN RATE
91% (228/250) — we LOSE 22 games. Our accuracy 40%, enemy 21%. Enemy avg speed
only 1.3 (SLOW). Games are LONG (avg 500, losses all 500-980 turns).

## Opponent profile (120 sims; header maps idx->name, enemy=non-'opus')
- movefrac 0.37, avg|v| 1.23 (SLOW), avg|dh| 0.011 (near-straight, tiny curve),
  engages ~300px. Conserves energy (fires ~half as often as us). All 22 losses
  are LONG energy-war grinds where our hit rate cold-streaked to ~25% at 200-300px
  and we bled to 0 while the enemy out-lasted us.

## ROOT CAUSE + FIX: W=1.0 -> W=0.75 (partial lead)
Fresh W-sweep (per-tick interception over recorded paths, TWO independent 80-game
slices), CLEAN + robust peak at W=0.75:
  slice A (files[:80]):    W0.5 38.3% W0.75 43.6% W1.0 35.1%
  slice B (files[120:200]):W0.6 41.9 W0.7 44.4 W0.75 44.7 W0.8 43.9 W0.9 39.8 W1.0 37.1
W=0.75 wins by ~9 points over head-on. NOTE: replay is BIASED toward W=1.0 (enemy
path was reactive to our ACTUAL W=1.0 shots) yet W=0.75 STILL wins big -> very
strong signal (bias would favor W=1.0, not against). This slow mover has a tiny
velocity/drift so a partial lead beats pure head-on.
Damage/net-energy model (120 games, current power tiers): W=1.0 dmg 105244 net
-5703 (BLEEDING -> the 22 losses) -> W=0.75 dmg 132668 (+26%) net +10155 (we now
GAIN energy). Higher HR = faster kills = fewer grind losses AND flips the energy war.

## Movement/power UNCHANGED (deliberate — tested, taper does NOT help)
Considered extending the energy-war taper to mid-range (dist>230 when behind), but
MODELED it over 150 games: net energy DROPPED 1096 -> 873. Our overall 200-300px
hit rate is 38% (net-POSITIVE), so tapering there sacrifices the positive shots in
the many WINNING games. The taper is not the fix; W=0.75 (higher HR everywhere) is.
Orbit ~245px, power tiers 3.0/<300 1.6/<400 1.0/<500 0.6/else, energy-war taper
(behind & dist>300 -> 0.8), fire gates, dodge ALL UNCHANGED.
Compiles Java 8 (major version 52). Backup of prior source: /tmp/MyTank.bak.java.

## For next teammate — VERIFY
- Want NEW /logs win rate ABOVE 91% (ideally 97%+), the 22 losses reduced, enemy
  score DOWN from 7909, killtick DOWN, our final E mean UP from 63. If it REGRESSED
  (new losses / share drop), the W=0.75 lead may have overshot -> raise W back
  toward 0.9-1.0, or revert to /tmp/MyTank.bak.java (git prior, W=1.0, 91%).
  Re-run the W-sweep on >=2 slices first BUT weight the REAL cross-round win rate
  far above the biased replay hit numbers.
- ite_m9 is SLOW/near-straight & conserves energy -> W=0.75 partial lead. If it
  becomes a FAST mover (avg|v|>4), lower W toward 0.25; if HEAVY spinner
  (avg|dh|>0.06), set W=0.0 (circular). Always re-check
  `head -1 /logs/rounds/0/sim_0.jsonl` for opponent name + INDEX MAPPING first.
- The remaining lever if grind losses persist is WAVE SURFING (high-risk, harness
  broken, trust /logs only). Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 verification pass, THIS pass) — opponent = it_economics__ite_m9

## STATUS: THE ROUND-1 W=0.75 GUN CHANGE WORKED BIG — NO CODE CHANGE THIS PASS
Verified /logs/rounds/{0,1} (opponent it_economics__ite_m9, SLOW near-straight
mover that CONSERVES energy; INDEX both rounds: i=0=ite_m9, i=1=opus):
- Round 0 (OLD W=1.0 head-on): opus 39510 vs ite_m9 7909 (84% share). trace WIN
  RATE 91% — LOST 22 games (long energy-war grinds).
- Round 1 (prior teammate switched gun aim W=1.0 -> W=0.75 partial lead): opus
  42916 vs ite_m9 3843. results_0.txt: opus_4_8.MyTank 1674 (92%), 10/10 firsts.
  HUGE improvement: full 250-sim sweep LOSSES = 1/250 (down from 22!), close(<20E)
  =1, ourFE min/mean = 0.0/103.9, mean killtick 257 (faster), enemy score HALVED
  (7909 -> 3843). The W=0.75 change (partial lead for this slow tiny-drift mover)
  is real-result-confirmed.

## Fresh W-sweep on ROUND-1 logs CONFIRMS W=0.75 is the peak (not just biased)
Per-tick interception over recorded round-1 paths (80 games), MONOTONIC PEAK at 0.75:
  W=0.0 0.436 | W=0.5 0.533 | W=0.6 0.546 | W=0.7 0.564 | W=0.75 0.576 |
  W=0.8 0.575 | W=0.9 0.553 | W=1.0 0.526.
Even though the replay is BIASED toward W=0.75 (round-1 enemy path was reactive to
our actual W=0.75 shots), the clean peak + the cross-round REAL result (W=1.0 lost
22 / W=0.75 lost 1) is decisive. KEEP W=0.75. Do NOT revert to head-on.

## The 1 loss (sim_243) is pure VARIANCE, NOT a fixable config bug
621-turn grind, behind on energy 78% of ticks, avg dist 286px. BUT our firing was
NET-POSITIVE: in-game hit rates 200-300px 0.36, 300-400px 0.53 (both above the 1/3
break-even). We simply had a cold-streak on defense (enemy landed its shots that
game). No firing gate / power taper would have helped — our shots were good. We win
10/10 firsts in EVERY 10-round battle, so this is NOT a match-level loss.

## Decision: NO code change (deliberate)
Source IDENTICAL to round-1 winning commit 64e7f21 (git diff on MyTank.java =
empty). W=0.75 (line 299), power tiers 3.0/<300 1.6/<400 1.0/<500 0.6/else, orbit
~245px, energy-war taper, fire gates, dodge (0.45) all data-optimal. Any edit only
risks regression on a 249/250 sweep we win with margin. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows a MATCH loss (enemy wins a 10-round battle) or win
rate collapsing. ite_m9 is SLOW/near-straight & conserves energy -> KEEP W=0.75
partial lead (fresh W-sweep peak confirmed). Do NOT revert to head-on off any bias
argument — the cross-round REAL result is decisive. If it becomes a FAST mover
(avg|v|>4), lower W toward 0.25; if HEAVY spinner (avg|dh|>0.06), set W=0.0
(circular). The remaining lever if grind losses ever RISE to a match threat is
WAVE SURFING (high-risk, harness broken, trust /logs only). Always re-check
`head -1 /logs/rounds/0/sim_0.jsonl` for opponent name + INDEX MAPPING first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = admiralrasmussen__wavesurfing (WE WERE LOSING 0/10 FIRSTS!)

## CRITICAL: this opponent is a NON-FIRING WAVE SURFER — we were LOSING EVERY ROUND
Round 0 result (BEFORE my change): admiralrasmussen__wavesurfing 15005 vs opus-4-8
2125. results_*.txt: enemy 600 (88-94%) with 10/10 FIRSTS EVERY battle; us 37-83
(6-12%), 0 firsts, 10 seconds. WE LOSE THE MATCH.

## ROOT CAUSE: we FIRED OURSELVES TO DEATH. The enemy does ZERO damage.
Verified across ALL 250 sim games:
- Enemy fires ZERO bullets (0 ticks with an enemy bullet in the air, all 250 games).
- Enemy never rams (0 ram dmg in results).
- So the enemy deals us NO damage at all. Yet WE DIE (ourE=0.0) while enemy keeps
  ~65-99 energy. In sim_0: we fired 69 shots, had 0 big energy drops (no wall/ram
  hits). We drained ~200 energy firing at a perfect dodger and bled to 0.
- Our hit rate vs this wave surfer = 2.2% (57 hits / 2602 fires over 40 games).
  It moves fast (avgV 4.9) and curves hard (avg|dh| 0.106) and SURFS our waves.
- Net energy/shot at 2% hit, power 3 = 0.02*9 - 3 = -2.8. Every shot bleeds us.
Enemy strategy = pure "outlast": never shoot, dodge perfectly, wait for our
suicide. It won 10/10 rounds as LAST SURVIVOR (500 survival + 100 bonus).

## THE FIX: PASSIVE-ENEMY CONSERVATION MODE (survive = win)
Since the enemy does 0 damage, our ONLY threat is self-inflicted bleed. If we DON'T
die, the round hits the turn limit with BOTH alive -> enemy gets NO last-survivor
bonus (that's the only way it beat us), and BULLET DAMAGE decides -> we win because
it fires 0 bullets and we land a few cheap ones.
Changes in aimAndFire (robots/custom/MyTank.java, fire-gate block ~line 354):
1. Track enemy fires (small energy drops) -> enemyFireCount. If t>120 and
   enemyFireCount<=4 (enemy is non-firing), enter conservation mode.
2. In conservation mode: power<=1.0 (close) / 0.5 (else); NO fire if getEnergy()<60
   (keep a big reserve so we can NEVER bleed to death); NO fire if dist>260;
   tight align (0.045); NO fire if getEnergy()<enemyEnergy. This keeps us alive
   INDEFINITELY while still landing a few close shots for bullet-damage points.
New fields: enemyFireCount, enemyEnergyHigh, turnCount. Enemy-fire detection added
in onScannedRobot (drop 0.09..3.05 = a fire). Threshold <=4 tolerates false counts
from OUR ~2%% hits (which also drop enemy energy).
Compiles Java 8 (major version 52). Backup of prior source: /tmp/MyTank.bak.java.

## For next teammate — VERIFY (this is a LOSING matchup we're trying to flip)
- Want NEW /logs: WE STOP DYING (our final E stays high, e.g. >60), the round goes
  to turn limit with both alive, enemy loses its last-survivor bonus, and OUR
  bullet damage > enemy's 0 -> we WIN or at least stop losing 0/10.
- If we STILL lose (enemy still gets firsts): we may still be bleeding somewhere.
  Check: are we hitting walls? (only 16 big drops / 250 games in round 0, so
  unlikely). Consider firing EVEN LESS (raise the getEnergy()<60 gate to <80, or
  STOP FIRING ENTIRELY in conservation mode -- allowFire=false always -- to
  guarantee survival; then it's a pure tie on survival, better than a 0/10 loss).
- If the enemy STARTS firing in a new round (enemyFireCount>4), conservation mode
  disables and the normal gun/movement (W=0.75, distance power tiers, orbit ~245px)
  takes over -- so this change is SAFE vs a firing opponent.
- Consider WAVE SURFING for OUR movement too (this enemy is a wave surfer; if it
  ever fires, dodging is key). High-risk, local harness broken -> trust /logs.
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = admiralrasmussen__wavesurfing — REWROTE CONSERVATION (was LOSING 0/10 both rounds)

## CRITICAL: Round-1 conservation mode NEVER TRIGGERED -> we still lost 0/10 firsts.
Both rounds: wavesurfing 600/602 (10/10 firsts) vs opus 37/80 (0 firsts). We die
(finalE 0.0) while enemy keeps 37-99 E. Enemy fires ZERO bullets, never rams ->
does 0 damage to us (verified: 0 big energy drops on us across games). ALL our
energy loss is SELF-INFLICTED firing at a ~2% dodger.

## WHY R1's conservation never activated (the bug)
Detection used enemyFireCount (enemy energy drops 0.09..3.05). BUT our OWN low-power
hits drop the enemy's energy into that same band -> 18 false "fires" counted in
sim_0 >> the <=4 threshold. So enemyPassive was ALWAYS false and we fired 78 shots
to death.

## THE FIX (this pass): DAMAGE-TAKEN based detection + energy-differential firing
1. New fields damageTaken (accumulated in onHitByBullet: 4p+2(p-1); onHitRobot:+0.6)
   and myFireCount. Detection: enemyPassive = (t>40) && (damageTaken < 5.0). This
   is immune to our own hits confounding it. If the enemy EVER starts dealing real
   damage, damageTaken>=5 -> we exit conservation and the normal gun/movement
   (W=0.75, orbit ~245px) takes over -> SAFE vs a firing opponent.
2. Endgame is decided by ENERGY DIFFERENTIAL (both idle -> inactivity drain; whoever
   has MORE energy survives). Verified: in losses the enemy idles at 37-88 E while
   we bled to 0. So the winning play is to STAY ABOVE the enemy's energy.
   In conservation: cheap close dead-on shots (power<=0.5/0.3, dist<240, align 0.04)
   fired ONLY when banking (ourE>75, to break the 100-100 parity by knocking the
   enemy down) OR ahead (ourE>enemyE+15). Stop when below 75 AND not ahead -> we
   can NEVER be dragged below the enemy -> we outlast it in the idle drain -> WIN.

## Compile: javac --release 8 ... -> major version 52 (Java 8). rc=0.

## For next teammate — VERIFY (this is the matchup we're flipping from 0/10)
- Want NEW /logs: our finalE stays HIGH (>enemy's), we STOP dying, we get FIRSTS.
- RISK: if we bank down to 75 but land ~0 hits (enemy dodges all), we could be at
  75 vs enemy ~100 and lose that game. If losses persist, the enemy's energy is
  staying too high -> either (a) lower the banking floor (75->85) so we spend less
  and stay nearer 100, accepting fewer enemy hits, OR (b) if enemy stays at 100 no
  matter what, the ONLY win is to end with MORE energy than it -> just DON'T FIRE at
  all in conservation (allowFire=false always) and rely on staying at ~100 vs the
  enemy's ~100 (coin-flip/draw, still better than 0/10). Check the enemy's actual
  final-E in the new logs to decide.
- Check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING first.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 3 / current pass) — opponent = admiralrasmussen__wavesurfing — POINT-BLANK REWRITE

## SITUATION: still LOSING the match (R0 2125, R1 2501, R2 4508 vs enemy ~11-15k)
R2 (conservation rewrite) improved us 2501->4508 and got 1/10 firsts, but we still
lose. Root cause fully diagnosed this pass.

## KEY MECHANIC: the Robocode INACTIVITY DRAIN decides this match
Enemy = non-firing wave surfer: fires ZERO bullets, never rams -> deals us 0 damage.
After 450 ticks with no damage dealt by anyone, BOTH bots lose 0.1 energy/tick and
race to 0. Whoever has MORE energy when that race starts survives longer = wins the
last-survivor bonus. Verified in sim_0: at t=200 we were 99.0 (we fired ONE -1.0
shot) vs enemy 100.0; both then drained in lockstep and we hit 0 exactly ~10 ticks
(=1 energy / 0.1) before the enemy. We lost 180/250 games by that tiny self-inflicted
deficit. We WON 65/250 (games where we happened to land enough early hits to get ahead).

## MEASURED our real hit rate vs this perfect dodger BY DISTANCE (250 R2 games,
## matching fires to enemy-damage events with bullet travel time):
##   <100px ~100% | 100-150px 54% | 150-200px 20% | 200-250px 14% | 250px+ <7%.
## Break-even hit rate for winning the energy differential = p/(8p-2): p3=14%, p1=17%,
## p0.5=25%. So firing is NET-POSITIVE only INSIDE ~150px (hugely so <100px). The old
## code fired mostly at 200-250px (14% = net-NEGATIVE) -> bled the 1-energy deficit.

## CHANGE THIS PASS (aim + movement, ONLY in enemyPassive mode)
enemyPassive = (t>40 && damageTaken<5.0)  [now a class field, set in aimAndFire,
read in doMovement]. When passive:
1. MOVEMENT: charge to POINT-BLANK (~90px). rangeBias -1.3 when dist>160 (nearly
   head-on inward), -0.6 to 110px, hold ~90px. Ramming is FREE (enemy does 0 damage)
   and disrupts its surfing -> closing is pure upside. Minimal reversal churn (no
   anti-GF dodging needed vs a 0-bullet enemy). Early `return` so the normal orbit
   logic doesn't run.
2. FIRE GATE: fire ONLY inside 150px. dist>150 -> allowFire=false (HOLD FIRE in the
   net-negative zone so we can NEVER self-inflict the losing deficit). <100px ->
   power 2.0 (~100% hit = huge energy swing). 100-150px -> power 1.0 (54% hit,
   net-positive). Extra safety: don't fire below 20E unless clearly ahead.
Normal (firing-enemy) gun/movement UNCHANGED -> SAFE vs every other opponent
(enemyPassive stays false the moment it deals us >=5 damage).
Compiles Java 8 (major version 52). Backup of prior source: /tmp/MyTank.bak.java.

## WHY THIS SHOULD FLIP THE MATCH
Old worst case: fire at range, miss, die 1 energy behind (the 180 losses).
New worst case: can't close -> HOLD FIRE -> tie at ~100 vs ~100 (a draw, still
better than a loss). New best/expected case: reach <150px where 54-100% of shots
land -> we get well ahead on energy -> we win the idle-drain last-survivor race.
The fire gate makes it IMPOSSIBLE to lose by self-inflicted bleed.

## For next teammate — VERIFY (this is the matchup we're flipping)
- Want NEW /logs: our finalE >= enemy's, we STOP dying first, we GAIN firsts and
  score. If we still lose: (a) we may not be closing enough -> the enemy surfs away
  faster than we close. Try stronger inward pull (rangeBias -1.5 when dist>160) or
  DISABLE wall-smoothing during the charge (it may deflect us). (b) If closing but
  still can't hit even <120px, the dodger is too good at point-blank -> fall back to
  PURE no-fire (set allowFire=false always in passive) to guarantee a tie/draw
  instead of a loss. (c) Check enemy's actual finalE in new logs to decide.
- If opponent CHANGES: enemyPassive auto-disables once it deals >=5 damage, and the
  normal gun (W per current tuning) + orbit take over. Always re-check
  `head -1 /logs/rounds/0/sim_0.jsonl` for opponent name + INDEX MAPPING first.
- Analysis: hit-rate-by-distance one-liner (match OUR energy drops 0.09..3.05 in
  t<450 to enemy energy drops >0.9 within 2-30 ticks, bucket by dist at fire time).
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 4 verification pass, THIS pass) — opponent = admiralrasmussen__wavesurfing — THE POINT-BLANK REWRITE FLIPPED THE MATCH

## STATUS: WE FLIPPED A 0/10 LOSS INTO A CRUSHING 10/10 WIN — NO CODE CHANGE
The round-3 point-blank-charge rewrite (charge to ~90px, HOLD FIRE outside 150px,
cheap point-blank shots inside) WORKED DECISIVELY:
- R0/R1/R2 (before/partial fixes): LOST every match (opus 2125/2501/4508 vs enemy
  15005/14926/10989, 0-1 firsts).
- R3 (point-blank rewrite): WON — opus 44411 vs wavesurfing 376. results_*.txt:
  opus_4_8.MyTank ~1780 (99-100%), 10/10 FIRSTS in EVERY 10-round battle.
- Full 250-sim sweep R3: LOSSES = 0/250, close(<20E) = 0/250. Our final energy
  min/mean = 95.4/124.7 (ENORMOUS margin). Enemy DIES every game (finalE 0.0).

## WHY IT WORKS (mechanic recap)
Enemy = non-firing wave surfer: fires ZERO bullets, never rams -> deals us 0 damage.
Old code fired at range (2% hit vs a perfect dodger) and self-inflicted the losing
1-energy deficit in the inactivity drain. The rewrite CHARGES to point-blank (~90px)
where ramming is free (enemy=0 damage) and our shots hit ~100%; it HOLDS FIRE
outside 150px (net-negative zone) so we can NEVER bleed to death. Result: we crush
it on bullet+ram damage AND survive to win the last-survivor race. See the detailed
Round-3 note above for the exact fire gates / rangeBias values (lines ~349-410 fire
gate, ~506-511 movement).

## Enemy still confirmed the passive fast surfer (R3 sim_0): avg|v| 4.91, movefrac
## 0.76, deals us 0 damage. enemyPassive = (t>40 && damageTaken<5.0) triggers
## correctly. If it EVER starts dealing >=5 damage, enemyPassive auto-disables and
## the normal gun (W=0.75)/orbit take over -> SAFE vs a firing opponent.

## Decision this pass: NO code change (deliberate)
Source is IDENTICAL to the round-3 winning commit 4b40324 (git diff HEAD on
MyTank.java = empty). Any edit only risks regression on a 250/250 sweep we win
with 95+ E to spare. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or the enemy starting to deal damage
(check damageTaken / enemy bullets in the air). wavesurfing is a PASSIVE non-firing
surfer -> KEEP the point-blank-charge + hold-fire-outside-150px conservation mode.
Do NOT re-enable long-range firing vs it (that's what lost us the match originally).
Mean killtick is ~606 (long games) — that's FINE (enemy does 0 damage, we win on
survival+bonuses regardless; faster kills would risk the strategy). Always re-check
`head -1 /logs/rounds/0/sim_0.jsonl` for opponent name + INDEX MAPPING first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = tannerrogalsky__tannerbot1 (COMPETITIVE, 34 losses)

## KEY FINDING: gun W was WRONG (0.75, leftover from SLOW ite_m9) for this SMOOTH moderate-fast mover + we bled at 300-400px
Round 0 result (BEFORE my change): opus 36806 vs tannerbot1 13672 (19% share —
highest firing-bot share in a while). results_0.txt: opus 1595 (81%), 10/10 firsts
BUT full 250-sim sweep: 34 LOSSES, 23 close(<20E), ourFE mean 42.6 (min 0.0),
games VERY LONG (avg 897, max 1319 turns), killtick mean 742. Genuinely competitive.

## Opponent profile (250 sims; header maps idx->name, enemy=non-'opus')
- movefrac 0.62, avg|v| 4.26 (moderate-fast), avg|dh| 0.019 (NEAR-STRAIGHT, smooth),
  engages ~324px. Conserves energy (fires ~half as often as us: 20.9 vs 35.8 in
  losses). It out-trades us in the long grinds.

## ROOT CAUSE of the 34 losses (measured, 150 games — DECISIVE)
Our REAL hit rate by distance (energy-drop=fire, enemy-energy-drop>3.5=our hit):
  0-100px 0.68 | 100-200 0.30 | 200-300 0.32 | 300-400 0.08(!) | 400-500 0.09.
We fired 2203 shots at 300-400px (8% hit = catastrophic net-negative bleed) and
spent most ticks at 200-400px, drifting to ~324px avg (WIDE of the ~245px target).
In losses our hit rate crashes to 30% while enemy's rises to 35% -> we bleed the
energy war. Enemy hit density: 0-100 15/1k, 100-200 12/1k, 200-300 9.9/1k, 300-400
6.9/1k (enemy gun MORE dangerous close, but 200-300 is the sweet spot: 32% our hit
+ moderate enemy density).

## CHANGES THIS PASS (3 levers; all attack the 300-400px bleed)
1. GUN aim W: 0.75 -> 0.0 (FULL LINEAR LEAD). W-sweep replay 2 slices MONOTONIC
   to full lead: W=0.0 0.156/0.134 vs W=0.75 0.109/0.093 vs W=1.0 0.095/0.084.
   This is a SMOOTH near-straight mover (like dacruzer) -> full lead best. The old
   W=0.75 (tuned for the SLOW ite_m9) was misaimed here.
2. POWER tiers: was 3.0/<300, 1.6/<400, 1.0/<500, 0.6/else. NOW 3.0/<250, 2.0/<320,
   0.8/<400, 0.4/<500, 0.2/else. Tapers HARD past 250px so each far miss barely
   costs energy (8% hit at 300-400 = pure bleed at full power).
3. FIRE GATE: hold fire past 320px when behind on energy (was 400px) — conserve in
   grinds instead of feeding the net-negative zone.
4. MOVEMENT rangeBias: stronger inward pull (>450->-1.2, >330->-0.9, >240->-0.5,
   <190->+0.5) to reach the ~225px net-positive zone instead of drifting to 324px.

## Validation (net-firing-energy model, MEASURED per-bucket hit rates)
OLD config @ old tick distribution: net -1023. NEW config (W=0.0 raises hr ~1.3x
+ closer orbit shifts ticks 300-400 -> 200-300): net +1040. Flips the energy war.
The direction is strongly supported; should convert most of the 34 grind losses.
enemyPassive mode STAYS OFF (enemy deals us 3-6 big hits/game -> damageTaken>=5),
so the normal gun/movement applies. Compiles Java 8 (major version 52). Backup:
/tmp/MyTank.bak.java (git prior = the 34-loss config).

## For next teammate — VERIFY
- Want NEW /logs: the 34 losses REDUCED (ideally <10), ourFE mean UP from 42.6,
  killtick DOWN from 742, enemy score DOWN from 13672, engagement dist DOWN from
  324 toward ~225px. If it REGRESSED (new losses / share drop): (a) the closer
  orbit may have exposed us to the enemy's stronger close gun (15/1k @0-100) ->
  push orbit back (thresholds 470/350/260/<210); (b) if W=0.0 overshoots (enemy
  became reactive/stop-and-go), raise W toward 0.5 and re-run the W-sweep;
  (c) full revert = /tmp/MyTank.bak.java (git prior, 34 losses but 81% share win).
- tannerbot1 is a SMOOTH moderate-fast near-straight mover -> KEEP W=0.0 full lead.
  If it becomes a HEAVY spinner (avg|dh|>0.06), circular already equals linear here.
- The remaining lever if grinds persist is WAVE SURFING (high-risk, harness broken).
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass, THIS pass) — opponent = tannerrogalsky__tannerbot1

## STATUS: THE ROUND-1 W=0.0 GUN + CLOSER-ORBIT + POWER-TAPER CHANGE WORKED HUGE — NO CODE CHANGE
Verified /logs/rounds/{0,1} (opponent tannerrogalsky__tannerbot1, a SMOOTH
moderate-fast near-straight mover: movefrac 0.62, avgV 4.26, avg|dh| 0.019,
engages ~324px, conserves energy):
- Round 0 (OLD W=0.75 leftover from SLOW ite_m9 + orbit ~245px + power 3.0/<300):
  opus 36806 vs tannerbot1 13672 (19% share). Full 250-sim sweep: 34 LOSSES,
  23 close(<20E), ourFE mean 42.6, killtick mean 742, turns avg 897 (long grinds).
- Round 1 (prior teammate: W=0.0 FULL LINEAR LEAD + power tiers 3.0/<250 2.0/<320
  0.8/<400 0.4/<500 0.2/else + stronger inward pull to ~225px + hold-fire >320px
  when behind): opus 44469 vs tannerbot1 5555. HUGE improvement:
    * full 250-sim sweep: LOSSES = 0/250 (down from 34!), close(<20E) = 1
    * enemy score 13672 -> 5555 (dropped 59%), our share 73% -> 89%
    * ourFE min/mean = 15.8/109.2 (up from 42.6), killtick 742 -> 390.6, turns
      avg 897 -> 541.7. Every metric improved decisively.
  tannerbot1 is a smooth near-straight mover so FULL LINEAR LEAD (W=0.0) is
  data-optimal (same as dacruzer); the old W=0.75 was misaimed. Confirmed.

## Decision this pass: NO code change (deliberate)
Source is IDENTICAL to the round-1 winning commit 5776dac (git diff on
MyTank.java = empty; only the .class was recompiled). W=0.0 (line 328), power
tiers 3.0/<250 2.0/<320 0.8/<400 0.4/<500 0.2/else, orbit ~225px w/ stronger
inward pull, hold-fire >320px when behind, energy-war taper, dodge (0.45), low-E
clamps. Any gun/movement edit only risks regression on a 250/250 sweep we now win
with margin. enemyPassive mode stays OFF (tannerbot1 deals us real damage ->
damageTaken>=5). Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). tannerbot1 is a SMOOTH moderate-fast
near-straight mover -> KEEP W=0.0 full linear lead. Do NOT switch to head-on off
any biased replay-sim (cross-round REAL result is decisive: W=0.75 lost 34, W=0.0
lost 0). If it becomes a REACTIVE stop-and-go dodger (avg|dh| up, moving frac
down, stops when we fire), full lead will overshoot -> raise W toward 0.5; re-run
the W-sweep on >=2 slices first. Always re-check
`head -1 /logs/rounds/0/sim_0.jsonl` for opponent name + INDEX MAPPING first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = vikdov__dominatorx (TOUGH, 71 losses)

## KEY FINDING: gun W was WRONG (0.0 circular, leftover from tannerbot1) for this STOP-AND-GO dodger + good gun
Round 0 result (BEFORE my change): opus-4-8 30463 vs vikdov__dominatorx 18973
(39% share — one of the HIGHEST enemy shares we've faced). results_0.txt: opus
1111 (61%), only 7/10 firsts (enemy got 3 firsts!). Full 250-sim sweep: 71 LOSSES,
92 close(<20E), ourFE mean 40.8 (min 0.0). This is a GENUINELY competitive foe
with an aggressive, accurate gun.

## Opponent profile (250 sims; header maps idx->name, enemy=non-'opus'; i=1=enemy R0)
- movefrac 0.82, avgV 4.59, avg|dh| 0.0685, engages ~256px. VELOCITY IS BIMODAL:
  33% of ticks at FULL speed (v=8), 15% stopped, rest spread -> a STOP-AND-GO /
  accelerate-decelerate DODGER, NOT a smooth circler. Fires ~22 shots/game = SAME
  as us (aggressive, NOT energy-conserving). Its gun hits us ~7-9/1k at ALL ranges
  100-400px (a good gun that stays effective at range).

## ROOT CAUSE of the 71 losses: our circular gun was NET-ENERGY-NEGATIVE
Under the OLD W=0.0 circular gun our real hit rate by distance (150 games):
  0-100px 0.99 | 100-200 0.38 | 200-300 0.25 | 300-400 0.21 | 400-500 0.26.
We engage ~256px (200-300 zone) at only 25% hit -> net -657 in the damage model
(BLEEDING). Enemy hit density stays high everywhere -> it out-trades us -> 71 losses.

## CHANGES THIS PASS (gun aim + orbit; both replay+model validated)
1. GUN aim W: 0.0 (circular) -> 1.0 (HEAD-ON). W-sweep replay 2 independent 80-game
   slices MONOTONIC to head-on: W=1.0 0.440/0.416 vs W=0.0 circular 0.265/0.245.
   CRITICAL: the replay is BIASED TOWARD W=0.0 (enemy path was reactive to our OLD
   circular shots) yet head-on wins by ~18pts DESPITE the anti-bias -> VERY strong
   signal (opposite of the usual spinbot/meow bias which favored the tested aim).
   A stop-and-go dodger defeats any lead (it stops/reverses) so head-on is correct
   (matches alpian__ianstank, trex22__deepthought). Damage/net-energy model (120
   games, w/ gunheat): W=0.0 dmg 15210 net -657 (bleeding) -> W=1.0 dmg 21734
   (+43%) net +3171 (we now GAIN energy). W=0.9 ties W=1.0 (net 3214) so head-on
   is clean-optimal.
2. MOVEMENT orbit ~225px -> ~190px. Our head-on HR is 38% at 100-200px vs 25% at
   200-300px while enemy density is similar (8.8 vs 7.8/1k) -> closer = more
   accurate at ~same defensive cost. rangeBias: >420 -1.2, >300 -0.9, >210 -0.5,
   <150 +0.5 (don't ram inside 100px where enemy density is 13.2/1k).
Power tiers (3.0/<250 2.0/<320 0.8/<400 0.4/<500 0.2/else), dodge (0.45..0.60 on
enemy fire), energy-war taper, fire gates, enemyPassive mode ALL UNCHANGED.
Compiles Java 8 (major version 52). Backup of prior source: /tmp/MyTank.bak.java.

## For next teammate — VERIFY
- Want NEW /logs: the 71 losses REDUCED (ideally <20), ourFE mean UP from 40.8,
  enemy score DOWN from 18973, share UP from 61%. If it REGRESSED (new losses /
  share drop): (a) head-on may have been over-fit to the biased replay — but the
  stop-and-go profile + anti-bias strongly support it; try W=0.75 hedge and re-run
  /tmp/wsweep.py + /tmp/dmg.py; (b) closer orbit may expose us to the enemy's good
  close gun -> push orbit back to ~230px (thresholds 450/330/240/<190); (c) full
  revert = /tmp/MyTank.bak.java (git prior = the 71-loss W=0.0 config, still won 61%).
- dominatorx is a STOP-AND-GO dodger with a GOOD gun -> KEEP W=1.0 head-on. If it
  becomes a SMOOTH constant-velocity mover (avg|dh| down, bimodal velocity gone),
  switch to W=0.0 (circular/linear lead). Re-run /tmp/wsweep.py on >=2 slices first
  BUT weight the anti-bias correctly (here bias favors circular, so head-on's win
  is trustworthy).
- The remaining lever if losses persist vs its good gun is WAVE SURFING (high-risk,
  local harness broken, trust /logs only).
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING.
- Tools: /tmp/wsweep.py (W-sweep 2 slices), /tmp/dmg.py (damage/net-energy model
  w/ gunheat) — rebuild from these notes if lost.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = vikdov__dominatorx — HEAD-ON GUN FINDING + ANTI-HEAD-ON MOVEMENT

## STATUS: R1 head-on-gun+orbit-190px change improved us (71->51 losses, 30463->34363,
## enemy 18973->16661). We WIN the match both rounds (winner=opus) but only ~61%
## share / 7/10 firsts — genuinely competitive foe. THIS pass attacks the 51 losses.

## KEY NEW FINDING: dominatorx's gun is HEAD-ON, not a lead gun
Measured enemy gun offset when firing (round-1 250 sims, 1566 fire events):
MEDIAN 0.018 rad, MEAN 0.049 rad off head-on-to-us. It aims at our CURRENT
position (head-on/pattern), NOT where we WILL be. This CHANGES the movement lever.

## ROOT CAUSE of the 51 losses (measured, not replay-biased):
- Distance IDENTICAL in losses vs wins (~240px both) -> NOT positional drift.
- We fire ~SAME as enemy (24.9 vs 24.0 shots/loss). Only diff: "behind on energy"
  72% of ticks in losses vs 15% in wins -> pure energy-war TRADE variance.
- Enemy hit density DROPS with range: 100-200px 10.5/1k, 200-300px 8.3/1k,
  300-400px 6.4/1k, 400-500px 4.1/1k.
- DECISIVE: enemy hits us with a RECENT REVERSAL 32.3% of the time, but reversals
  only happen 15.9% of ticks -> hits are ~2x MORE LIKELY right after we reverse.
  Exactly the head-on-gun signature: reversing brings us back toward the bullet's
  landing spot (aimed at our old position) AND kills our lateral velocity.
- Losses had LOWER lateral speed (3.81 vs 4.08 in wins). Steady tangential motion
  at full lateral speed is what beats a head-on gun.

## CHANGES THIS PASS (both attack enemy hits; gun aim UNCHANGED at W=1.0 head-on)
1. MOVEMENT orbit ~190px -> ~215px (rangeBias thresholds 430/320/235/-0.45, push
   out <175). Cuts enemy hit density ~20% (200-300px 8.3/1k vs 100-200px 10.5/1k);
   our head-on hit rate holds well with range.
2. FIRE-TRIGGERED REVERSAL 0.45 -> 0.15 (rate-limit 6->8), random 0.07->0.06
   (rate-limit 8->12). Reversing on enemy fire is COUNTERPRODUCTIVE vs a head-on
   gun (the ~2x hit multiplier above). Favor steady full-lateral-speed orbit.
   (The 0.45 was tuned for the LEAD-gun juggernaut -- opposite gun type.)
Gun (W=1.0 head-on, confirmed via W-sweep monotonic to head-on 44-47%), power
tiers, energy-war taper, fire gates, enemyPassive mode ALL UNCHANGED. enemyPassive
stays OFF (dominatorx deals us real damage -> damageTaken>=5). Backup: /tmp/MyTank.bak.java.
Compiles Java 8 (major version 52), rc=0.

## For next teammate — VERIFY
- Want NEW /logs: losses BELOW 51 (ideally <25), ourFE mean UP from 50.8, enemy
  score DOWN from 16661, share UP from 61%. If it REGRESSED (new losses / share
  drop): (a) wider orbit may have cut our own hit rate more than expected -> pull
  orbit back to ~200px (thresholds 420/310/225/-0.5, push out <165); (b) if fewer
  reversals made us a fixed pattern the enemy learned (unlikely for a head-on gun
  but possible if it's actually pattern-matching), raise the random reversal back
  to 0.10; (c) full revert = /tmp/MyTank.bak.java (git prior, R1 config = 51 losses
  but still WON the match).
- dominatorx is a STOP-AND-GO dodger with a HEAD-ON gun -> KEEP W=1.0 head-on and
  STEADY tangential movement (low reversals). Do NOT raise fire-triggered dodge
  (that's for LEAD guns; it's ~2x WORSE here per the reversal-hit correlation).
- CAVEAT: reversal change is GLOBAL. Vs a LEAD gun (juggernaut) higher dodge helped.
  If a future lead-gun foe regresses, the principled fix is to DETECT the enemy gun
  type (measure its fire offset like this pass) and set dodge probability by type,
  rather than a single global constant. That's the real next improvement.
- The remaining big lever vs its head-on gun is WAVE SURFING (high-risk, harness
  broken, trust /logs only). Always re-check `head -1 /logs/rounds/0/sim_0.jsonl`
  for opponent + INDEX MAPPING first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 1 / current pass) — opponent = alexbay218__shreker (COMPETITIVE, 25 losses)

## KEY FINDING: gun aim W=1.0 head-on is CORRECT; the lever is ORBIT DISTANCE + POWER
Round 0 result (BEFORE my change): opus-4-8 35243 vs alexbay218__shreker 13397
(33% share). results_0.txt: opus 1190 (67%), 8/10 firsts (enemy got 2 firsts!).
Full 250-sim sweep: 25 LOSSES, 41 close(<20E), ourFE mean 52.7 (min 0.0), games
LONG (avg 493, max 1123). Genuinely competitive foe.

## Opponent profile (250 sims; header maps idx->name, enemy=non-'opus'; i=0=enemy R0)
- movefrac 0.70, avgV 2.66 (moderate), avg|dh| 0.020 (NEAR-STRAIGHT), engages ~249px.
- Enemy gun: MEASURED offset ~0.10 rad off head-on when firing -> a HEAD-ON gun
  (not a lead gun). So low fire-triggered reversal (0.15, already set) is correct.

## Gun aim W=1.0 head-on CONFIRMED optimal (W-sweep 2 slices, MONOTONIC)
Per-tick interception over recorded paths: W=0.0 0.31, W=0.5 0.37-0.38, W=0.75
0.41, W=0.9 0.49, W=1.0 0.50-0.51. Clean monotonic to head-on. KEPT W=1.0.
(This matches a near-straight moderate mover; NOT the tannerbot1 full-lead case.)

## ROOT CAUSE + FIX: we orbited too CLOSE (215px) in the enemy's kill zone
MEASURED enemy hit density by distance (unbiased -- its ACTUAL hits on us, 250 games):
  0-100px 9.8/1k | 100-200px 11.1/1k | 200-300px 5.8/1k | 300-400px 3.6/1k |
  400-500px 1.5/1k. Enemy is ~3x MORE dangerous at 100-200px than 300-400px.
OUR head-on hit rate by distance (replay, 120 games) HOLDS ~50% out to 500px:
  100-200 0.44 | 200-300 0.52 | 300-400 0.51 | 400-500 0.55.
=> Orbiting WIDER is strictly better: same/better accuracy, far fewer enemy hits.
Net-energy model (measured density + replay hr): 100-200px pw3 = -17.7/1k (BLEED!),
200-300px +64, 300-400px pw3 +74, 400-500px pw2 +82. Same insight that beat
lead-gun/curving foes (spinbot ~250px, jeujdapeu ~245px, dominatorx).

## CHANGES THIS PASS (movement + power; gun aim UNCHANGED at W=1.0 head-on)
1. MOVEMENT: orbit ~215px -> ~320px. rangeBias: >520 -1.2, >400 -0.8, >330 -0.35,
   <280 +0.5. Targets the 300-400px zone (3.6/1k enemy hits vs 11.1 at 100-200).
2. POWER tiers: was 3.0/<250 2.0/<320 0.8/<400 0.4/<500 0.2/else (tuned for
   tannerbot1 whose hit crashed at 300px). NOW 3.0/<350 2.0/<450 0.8/<550 0.3/else
   -- our hit rate HOLDS ~50% out to 500px here so keep power high. Replay damage
   model over 120 games: OLD dmg 23432 -> NEW 26316 (+12%).
3. FIRE GATE: hold-fire-when-behind threshold 320px -> 450px (net-positive out to
   450px now, only gate the truly-far low-hit shots).
Dodge (0.15 fire-reversal, correct vs head-on gun), energy-war taper, low-E
clamps, enemyPassive mode ALL UNCHANGED. enemyPassive stays OFF (shreker deals us
real damage -> damageTaken>=5). Compiles Java 8 (major version 52). Backup:
/tmp/MyTank.bak.java (git prior = the 25-loss config, still WON 67% share).

## For next teammate — VERIFY
- Want NEW /logs: the 25 losses REDUCED (ideally <10), ourFE mean UP from 52.7,
  enemy score DOWN from 13397, share UP from 67%, engagement dist UP from 249 to
  ~320px. If it REGRESSED (new losses / share drop): (a) the wider orbit may have
  cut our OWN hit rate more than the biased replay suggested -> pull orbit back to
  ~270px (thresholds 480/360/290/<250) and power to 3.0/<300; (b) full revert =
  /tmp/MyTank.bak.java (git prior, 25 losses but 67% share WIN).
- shreker is a MODERATE near-straight mover with a HEAD-ON gun -> KEEP W=1.0
  head-on + steady low-reversal orbit. Do NOT raise fire-triggered dodge (that's
  for LEAD guns; it's counterproductive vs a head-on gun). If it becomes a FAST
  curving dodger (avg|dh|>0.06), set W=0.0 (circular). Re-run the W-sweep + the
  enemy-hit-density-by-distance analysis first (the density signal is UNBIASED and
  was decisive this pass). Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for
  opponent name + INDEX MAPPING first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 2 / current pass) — opponent = alexbay218__shreker

## STATUS: R1's orbit-wider (215->320px) change improved us 67%->73% (8->9 firsts,
## enemy 13397->12283). R1 full 250-sim sweep: 17 LOSSES (down from R0's 25),
## close(<20E) 21, meanOFE 48.7, meanTurns 530, meanDist 319px.

## KEY FINDING (lag-corrected, DECISIVE): our hit rate is FLAT ~29% at ALL ranges;
## enemy hit density DROPS sharply with range -> orbit WIDER
Measured our hit rate (matched each fire to a subsequent enemy dmg event with
bullet-travel lag) + enemy hit density by distance (round-1 250 sims):
  100-200px: ourHR 0.31, enemyHits 12.6/1k
  200-300px: ourHR 0.29, enemyHits  4.9/1k
  300-400px: ourHR 0.28, enemyHits  5.2/1k  (we spent 73820 ticks here — most time)
  400-500px: ourHR 0.29, enemyHits  2.6/1k
  500-600px: ourHR 0.22, enemyHits  1.7/1k
Our accuracy is essentially RANGE-INDEPENDENT (28-33%), but the enemy hits us ~5x
LESS at 400-500px than 100-200px. LOSSES vs WINS: IDENTICAL engagement dist (319px
both) -> NOT positional; pure energy-war VARIANCE (behind on energy 66% of loss
ticks vs 18% in wins). Since firing is ~net-neutral (29% hit ~= 33% break-even),
we win via SURVIVAL, not the per-shot trade -> minimize enemy hits = orbit wider.

## Enemy = MODERATE near-straight mover with a HEAD-ON gun (confirmed)
movefrac 0.74, avgV 2.79, avg|dh| 0.017. Enemy gun offset when firing: median
0.139 rad (aims ~current pos = HEAD-ON gun) -> steady tangential motion (low
fire-triggered reversal, already 0.15) is correct; do NOT raise dodge (that's for
LEAD guns). Gun aim W-sweep (2 slices, biased toward the tested aim): round-0
favors W=1.0 (33.4%), round-1 favors W=0.9 (28.1%) — within ~1-2pt noise. KEPT
W=1.0 head-on (don't chase small biased-replay diffs; matches near-straight mover).

## CHANGE THIS PASS (movement + power; gun aim UNCHANGED at W=1.0 head-on)
1. MOVEMENT: orbit ~320px -> ~370px. rangeBias thresholds 520/400/330/<280 ->
   560/440/380/<340 (push out inside 340px, hold ~370px). Camps the 300-400px zone
   (~4-5/1k) fully instead of straddling into 200-300 higher-density; enemy hits us
   less with ~no accuracy loss (flat HR).
2. POWER: 3.0/<350 -> 3.0/<400 (keep full power at the wider orbit so DAMAGE OUTPUT
   is UNCHANGED — kill speed preserved), 2.0/<500, 0.8/<580, 0.3/else.
3. FIRE GATE: hold-fire-when-behind 450px -> 500px (HR flat to 500px).
Net-energy model (measured flat HR + density): 320px -162/1k -> 370px -154/1k,
SAME ourDmg/1k 1450 (power stays 3.0). Modest but positive, ZERO damage-output
downside. Dodge (0.15 fire-reversal), energy-war taper, low-E clamps, enemyPassive
mode ALL UNCHANGED. enemyPassive stays OFF (shreker deals real dmg -> damageTaken>=5).
Compiles Java 8 (major version 52), rc=0.

## For next teammate — VERIFY
- Want NEW /logs: losses BELOW 17 (ideally <10), meanOFE UP from 48.7, enemy score
  DOWN from 12283, share UP from 73%, engagement dist UP from 319 toward ~370px.
  Kill speed should be similar (power unchanged at 3.0/<400) — if games run to the
  turn limit WITHOUT kills (enemy survives with energy), the orbit is too wide ->
  pull back to ~340px (thresholds 540/420/360/<320). If it REGRESSED (new losses),
  revert to git prior (R1 config = 320px orbit, 17 losses, 73% share WIN).
- shreker is a MODERATE near-straight mover with a HEAD-ON gun -> KEEP W=1.0 head-on
  + steady low-reversal orbit. The gain from orbit-wider is modest; the remaining
  big lever vs its head-on gun + energy-war variance is WAVE SURFING (high-risk,
  local harness broken, trust /logs only). Always re-check
  `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING first.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = pez__wallspoet (WORST MATCHUP: 88 losses)

## KEY FINDING: pez__wallspoet is an ACTIVE WAVE SURFER — we bled ourselves to death
Round 0 result (BEFORE my change): opus-4-8 26470 vs pez__wallspoet 19617 (30% share!).
results_0.txt: opus 1194 (70%), 8/10 firsts (enemy got 2 firsts). Full 250-sim sweep:
88 LOSSES, 47 close(<20E), ourFE mean only 30.0 (min 0.0), games VERY LONG (avg 716,
max 1168). This is our TOUGHEST matchup in a long time.

## Opponent profile (250 sims; header maps idx->name, enemy=non-'opus'; i=1=enemy R0)
- movefrac 0.51, avgV 2.96, avg|dh| 0.0125 (near-straight), engages ~377px.
- It FIRES back and deals ~55 dmg/game (we deal ~59). BOTH bots have ~5% hit rate.

## ROOT CAUSE: we FIRED 2x MORE than the enemy at the SAME ~5% hit rate -> bled 2x
- OUR fires 5147, hits 288, HR 5.6%. ENEMY fires 2879, hits 119, HR 4.1%.
- We wave-surf-proof? NO. Our REAL hit rate is ~5% at EVERY distance (replay says
  ~35% but that's biased — the enemy dodges based on our ACTUAL bullets, i.e. it's
  a WAVE SURFER). We can't hit it anywhere.
- Net-firing-energy model: full-range power3 = -11271; cap<=250px power<=2 = -1216
  (~9x less bleed). Every config is net-negative (dodger), but firing LESS/CLOSER
  bleeds far less.
- Losses vs wins: SAME engagement dist (377 vs 367px) -> NOT positional. Losses =
  behind on energy 73% of ticks (vs 24% wins). Pure energy-war: we out-fire ourselves.
- Enemy hit density by distance: 100-200px 12.9/1k (WORST for us), 300-500px ~5-6/1k
  (SAFEST). So closing is MORE dangerous defensively AND doesn't raise our accuracy.

## CHANGES THIS PASS (CONSERVE ENERGY — the only lever vs a wave surfer)
1. POWER tiers LOWERED: was 3.0/<400 2.0/<500 0.8/<580 0.3/else. NOW 2.0/<250,
   1.2/<400, 0.6/<500, 0.3/else. Lower power = cheaper misses vs the perfect dodger.
2. FIRE GATE tightened: HOLD FIRE past 450px ALWAYS (pure suicide); hold past 350px
   when behind on energy. Stops the catastrophic long-range bleed (we fired 2603
   shots at 300-400px for 6% hit).
3. MOVEMENT orbit ~370px -> ~420px (rangeBias 580/470/420/<380). Camps the
   min-enemy-hit zone (400-500px ~5/1k vs 100-200px 12.9/1k), just under the fire cap.
4. GUN aim W 1.0 -> 0.5 (replay peak, minor since real HR ~5% regardless).
enemyPassive mode stays OFF (wallspoet deals real damage -> damageTaken>=5), so the
normal (now conservation-tuned) gun/movement applies. Compiles Java 8 (major 52).
Backup of prior source: /tmp/MyTank.bak.java (git prior = the 88-loss config).

## For next teammate — VERIFY (this is our WORST matchup, trying to flip losses)
- Want NEW /logs: the 88 losses REDUCED, ourFE mean UP from 30.0, enemy score DOWN
  from 19617, share UP from 70%. The theory: firing ~half as much at lower power
  flips the energy differential (both HR ~5%, we were firing 2x more = bleeding 2x).
- IF IT REGRESSED (more losses / share drop): (a) we may now be TOO passive and the
  enemy's ~55 dmg/game out-damages our reduced offense -> raise the fire cap back to
  500px or power to 1.5/<400; (b) if games time out with enemy alive & higher energy,
  we're not dealing enough -> allow more mid-range firing; (c) full revert =
  /tmp/MyTank.bak.java (git prior, 70% share / 88 losses but still WON the match).
- The ONLY robust win vs a wave surfer is WAVE SURFING our own movement (track enemy
  bullet waves, move to min-danger GF). High-risk, local harness broken -> trust /logs.
  That's the real next lever if conservation isn't enough.
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = pez__wallspoet — REVERTED R1 CONSERVATION REGRESSION

## CRITICAL: R1's energy-conservation change LOST the match — reverted to R0 (winning) config
Opponent = pez__wallspoet, an ACTIVE WAVE SURFER (fires back power-3, dodges near-
perfectly). BOTH bots hit ~5%% at ALL ranges. This is a SLUGFEST decided by SCORE
components (bullet damage + bonus + survival), NOT by who "wins" survival games.

## REAL cross-round MATCH results (results.json winner — the decisive signal):
- ROUND 0 (AGGRESSIVE gun: power 3.0/<400, 2.0/<500; fire out to 550px; orbit
  ~370px; W=1.0): WON — opus 26470 vs wallspoet 19617. We out-fired the enemy
  (~1.75x shots) and won the bullet-damage race (we deal 59.4 vs take 55.0/game).
- ROUND 1 (prior teammate's CONSERVATION: power 2.0/<250 1.2/<400 0.6; HOLD FIRE
  >450px; orbit ~420px; W=0.5): LOST — opus 23183 vs wallspoet 23824. winner=
  pez__wallspoet! results_0.txt: enemy 1083 (54%) vs us 928 (46%). We WON survival
  (300 vs 200, 6 firsts vs 4) but LOST bullet dmg badly (494 vs enemy 761) ->
  lost the MATCH. Conservation made us fire LESS than the enemy (3150 vs 3349),
  conceding the bullet-damage/bonus score that decides this slugfest.

## KEY LESSON: survival-game count is NOT the match outcome
R0 and R1 had ~IDENTICAL sim survival (R0 160/88, R1 161/86 wins/losses) yet R0
WON the match and R1 LOST it. The difference was BULLET DAMAGE score. Against a
FIRING wave surfer that shoots power-3, you must OUT-FIRE it (aggression), not
conserve. Conservation only wins vs a NON-FIRING passive surfer (admiralrasmussen,
handled by enemyPassive mode). wallspoet FIRES -> enemyPassive stays OFF
(damageTaken>=5 after its first hit) -> normal aggressive gun applies. GOOD.

## CHANGE THIS PASS: reverted to R0 config + small bullet-damage bump
1. Restored the R0 winning config via `git show HEAD~1:...MyTank.java` (power
   3.0/<400 2.0/<500 0.8/<580 0.3/else, W=1.0, fire out to 550px, orbit ~370px).
2. Bumped the 400-500px power tier 2.0 -> 2.5 (line 261). 27%% of our ticks are in
   this bucket (45%% at 300-400px, 27%% at 400-500px). +25%% dmg/hit (11->13.75)
   for a small energy/cooldown cost -> widens our bullet-damage margin in the
   slugfest R0 already wins, with minimal survival risk (we're already ahead on
   the damage exchange 59.4 vs 55.0/game).
Compiles Java 8 (major version 52). rc=0.

## For next teammate — VERIFY
- Want NEW /logs: winner=opus-4-8 (results.json), our bullet dmg > enemy's in
  results_0.txt, score margin held/raised above R0's 26470 vs 19617.
- If we LOSE again: (a) the 2.5 bump may have hurt survival -> revert line 261 to
  2.0 (exact R0 config = proven MATCH WIN). (b) DO NOT lower power / conserve
  (R1 proved that LOSES the match — we concede bullet damage to the firing enemy).
  (c) If bullet dmg is still close, try MORE aggression: power 3.0 out to 500px
  and remove the 550px fire gate entirely (fire everywhere) to maximize shots.
- The ONLY robust way to BEAT (not just out-trade) a wave surfer is WAVE SURFING
  our own movement (track enemy bullet waves, move to min-danger GF) — high-risk,
  local harness broken, trust /logs only. That's the real untapped lever.
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 3 / current pass) — opponent = pez__wallspoet (LEAD-GUN WAVE SURFER)

## STATUS going in: WINNING the match but competitive (7/25 battles lost)
Cross-round results (results.json, opponent FIXED = pez__wallspoet all rounds):
- R0 (aggressive gun): WON 26470 vs 19617.
- R1 (conservation): LOST 23183 vs 23824 (under-fired -> conceded bullet dmg).
- R2 (reverted aggressive + power bump): WON 25436 vs 21164, 18/25 battles.
Full 250-sim sweep R2: survival wins 141 / losses 96, ourFE mean 25.6, games VERY
long (avg 737, max 1550). It's a firing LEAD-GUN wave surfer slugfest.

## SCORE-COMPONENT breakdown (R2, 25 battles) — SURVIVAL is our biggest lever
  opus : survival 7500, survBonus 1500, bulletDmg 14398, bulletBonus 2017
  enemy: survival 4900, survBonus  980, bulletDmg 13731, bulletBonus 1524
Our margin comes MOSTLY from SURVIVAL (+2600) — bullet dmg is nearly tied (+667).
=> To win by MORE, SURVIVE MORE (we lose 96/250 survival games). Better dodging
raises survival AND keeps energy to keep firing (bullet dmg complementary).

## KEY MEASUREMENT: wallspoet uses a LEAD gun (NOT head-on)
Measured enemy gun offset when firing (50 R2 sims, 999 events): MEDIAN 0.55 rad,
MEAN 0.62 rad off head-on-to-us. It aims where we WILL be. Against a LEAD gun the
correct evasion is to REVERSE / change lateral direction on its fire so the
already-fired lead shot flies to the far side and misses. (Contrast dominatorx =
head-on gun, offset 0.018 rad, where reversing was BAD.)

## CHANGE THIS PASS (movement dodging only; gun/power/orbit UNCHANGED)
1. dodge-on-fire 0.15 -> 0.30 (rate-limit 8->7 ticks). LEAD gun -> reverse to
   dodge its waves. (The 0.15 was tuned for dominatorx's head-on gun — wrong here.)
2. uncorrelated random reversal 0.06 -> 0.11 (rate-limit 12->11) to break any
   residual movement period a wave surfer could profile.
3. onHitByBullet reversal 0.5 -> 0.65 (a hit = we were profiled -> disrupt harder).
Kept MODERATE (not 0.7+) because R2-loss data was CONFOUNDED (hits appear higher
right after reversing, but that's because we already reverse REACTING to fire —
can't cleanly separate). The lead-gun measurement (0.55 rad) is unambiguous and
anti-lead reversal is textbook, so a moderate raise is the reasoned play. Gun
(W=1.0 head-on), power tiers (3.0/<400 2.5/<500 0.8/<580 0.3/else), orbit ~370px,
enemyPassive mode ALL UNCHANGED. Compiles Java 8 (major version 52). Backup:
/tmp/MyTank.bak.java (= R2 winning config).

## For next teammate — VERIFY (this is competitive; measure carefully)
- Want NEW /logs: survival wins UP from 141/250, our survival SCORE up from 7500,
  fewer battle losses than 7/25, match margin held/raised above R2's 25436 vs 21164.
- IF IT REGRESSED (more battle losses / lower survival): the higher dodge may have
  made us MORE hittable (reversing kills lateral speed briefly, or became learnable)
  -> REVERT to /tmp/MyTank.bak.java (git prior = R2 config, 18/25 WIN). This is the
  #1 risk — movement changes have historically backfired (see dominatorx/juggernaut
  notes). If it worked, could push dodge to 0.40.
- DO NOT lower power / conserve energy (R1 proved that LOSES the match by conceding
  bullet damage to this FIRING enemy). Keep aggressive gun.
- The ONLY robust way to truly BEAT a wave surfer is WAVE SURFING our own movement
  (track enemy bullet waves, move to min-danger GuessFactor). High-risk, local
  harness broken (can't validate) -> trust /logs only. That's the real untapped lever.
- Opponent is FIXED = pez__wallspoet. Always re-check `head -1 /logs/rounds/0/sim_0.jsonl`.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 4 / current pass) — opponent = pez__wallspoet — REVERTED R3 DODGE REGRESSION

## CRITICAL: R3's higher-dodge change LOST the match — reverted to R2 (winning) config
Opponent still = pez__wallspoet (LEAD-gun ACTIVE WAVE SURFER). Cross-round MATCH
results (results.json winner — decisive):
- R0 (aggressive gun, power 3.0/<400 2.0/<500, dodge 0.15/0.06/0.5): WON 26470 vs
  19617 (biggest margin +6853; 161 firsts).
- R1 (CONSERVATION power 2.0/1.2/0.6, hold-fire >450px, W=0.5): LOST 23183 vs
  23824 (under-fired -> conceded bullet dmg despite 164 firsts).
- R2 (reverted aggressive + 400-500px power bump 2.0->2.5): WON 25436 vs 21164
  (+4272; 152 firsts).
- R3 (prior teammate RAISED dodge-on-fire 0.15->0.30, random reversal 0.06->0.11,
  onHitByBullet 0.5->0.65): LOST 23973 vs 24141. firsts CRASHED 152->129 (enemy
  100->122). The "reverse more vs its lead gun" theory BACKFIRED — reversing kills
  our lateral speed / became learnable, so we got hit MORE and lost survival (our
  biggest score component). One battle showed us at only 25% (1/10 firsts).

## THIS PASS: reverted the 3 dodge params to R2 values (code-verified identical to R2)
- dodge-on-fire 0.30 -> 0.15 (rate-limit 7 -> 8)
- uncorrelated random reversal 0.11 -> 0.06 (rate-limit 11 -> 12)
- onHitByBullet reversal 0.65 -> 0.5
Verified `diff` (ignoring comments) of MyTank.java vs git 4e7a778 (R2 winning
commit) = IDENTICAL. Gun (W=1.0 head-on), power tiers (3.0/<400 2.5/<500 0.8/<580
0.3/else), orbit ~370px, fire gates, enemyPassive mode ALL unchanged.
Compiles Java 8 (major version 52). rc=0. R3 (bad) backup at /tmp/r3_backup.java.

## KEY LESSON (reinforces the whole README): movement/dodge changes REPEATEDLY backfire
R3 (dodge up) LOST, just like R1 (conservation) LOST and the earlier juggernaut
R1 (dodge up 0.45->0.70) REGRESSED 15->26 losses. Vs this wave surfer the proven
winning play is the R0/R2 AGGRESSIVE gun (out-fire it on bullet damage) with the
R2 dodge params (0.15). Do NOT raise dodge, do NOT conserve. Both lose the match.

## For next teammate
- Opponent is FIXED = pez__wallspoet (all rounds). We WIN with the R2 config (now
  restored). If a future pass wants MORE margin, the ONLY safe lever tried so far
  is bullet-damage aggression (R0->R2 power bump was neutral-to-positive). Do NOT
  touch movement/dodge (R3 proved it LOSES). Do NOT conserve (R1 proved it LOSES).
- The only robust way to truly BEAT (not just out-trade) a wave surfer is WAVE
  SURFING our own movement — high-risk, local harness broken (trust /logs only).
  If attempted, validate carefully and be ready to revert to the R2 config.
- Verify NEW /logs: want winner=opus-4-8, ~55% share like R2, firsts ~152.
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 5 / current)

## OPPONENT NOW: josephjeon__gntest (a REAL, aggressive bot)
Prior notes about stationary/passive opponents are OBSOLETE for this matchup.
Analyzed /logs/rounds/0/ (250 sims). Baseline (before my edits): we WON 70% of
games (173-74) and led score 26867 vs 16527 (~62%). But 2 of 25 matches lost.

### Opponent profile (analysis scripts in /tmp saved logic below):
- FAST curving mover: avg|v| 6.39, 74% full speed, moves 89% of ticks, avg|dh| 0.083 rad/tick.
- AGGRESSIVE: fires ~29 shots/game (NOT energy-conserving).
- Gun is HEAD-ON: median offset 0.022 rad (aims at our current position).
- We are hard for it to hit AND it's hard for us to hit (~5-7% both ways = slugfest).

### KEY DEFENSIVE FINDING (biggest lever = survival, our top score component):
Enemy hit density on us by distance (hits/1000 ticks):
  100-500px: ~58-65/1k (FLAT, high)   500-600px: 27.6   600-700px: 18.2
=> Enemy accuracy COLLAPSES beyond 500px. We were orbiting ~370px (in its kill
zone). Moved orbit to ~530px (rangeBias tiers, ~line 580) to slash damage taken.

### Aim: W=0.0 (full linear lead) beats W=1.0 head-on (7.6% vs 5.0% replay).
Changed W 1.0 -> 0.0 (line ~334). Fast constant-velocity mover needs a full lead.

### Power: tiered by distance (line ~266): 3.0<200, 2.0<450, 1.5<560, 0.8<640, 0.4 far.
Point-blank (enemy charging in) is ~80% hit -> full power. At our 530px orbit,
moderate power keeps our bullet-dmg lead (we win 14064 vs 10918) without heavy bleed.
Relaxed far-fire gates to 600/640px so we still fight at our 530px orbit.

## ANALYSIS SCRIPTS (rebuild in /tmp; logic documented):
- Enemy movement/gun profile: parse sim_*.jsonl, robot i=1 is enemy. Check v (velocity),
  bh (body heading) deltas, energy drops 0.09-3.05 = a fire, gun offset gh vs atan2 to us.
- Enemy hit density on us: count our energy drops 0.09-3.05 per distance bucket.
- Aim replay: for each tick, compute our aim (W blend of head-on vs iterated lead),
  fly bullet dist/bs ticks, check if it lands within 18px of enemy's ACTUAL future pos.
  NOTE: replay UNDERSTATES hit rate (enemy path is reactive to our old shots) but W/dist
  RANKINGS are still valid.

## Backup of pre-edit bot: robots/custom/MyTank.java.bak
## Verify build: javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java
##   javap -v robots/custom/MyTank.class | grep "major version"  # want 52

## NEXT TEAMMATE: if we regress, the orbit-wider (530px) change is the main bet.
If losses go UP, try orbit ~450px (between old 370 and new 530). If bullet-dmg
share drops too much, raise the 1.5 tier at 560px back toward 2.0.

# Agent Notes (Round 2 / current pass) — opponent = josephjeon__gntest

## STATUS: R1's orbit-wider (370->530px) + W=0.0 + tiered-power change WORKED BIG
Cross-round MATCH results (results.json, opponent FIXED = josephjeon__gntest):
- R0 (aggressive close-orbit config): WON 26867 vs 16527 (62% share).
- R1 (prior teammate: orbit ~530px, W=0.0 full lead, distance-tiered power): WON
  36723 vs 9409 (80% share!). enemy score nearly HALVED. Full 250-sim sweep:
  243/250 wins, 7 losses (all energy-war/hit-rate VARIANCE at ~340-514px, behind
  on energy 41-86% of ticks), ourFE mean 76.7 (up from ~25). Huge improvement.

## Opponent = FAST curving mover (avgV 6.39, 74% full speed, avg|dh| 0.083) with an
## AGGRESSIVE HEAD-ON gun (fires ~29/game, gun offset ~0.022 rad). Mutual slugfest
## (~5-7% both ways) decided by SURVIVAL (our top score component) + bullet dmg.

## KEY UNBIASED SIGNAL (round-1 enemy-hit-density by distance, its ACTUAL hits on us):
##   0-100px 25.4/1k | 100-200 12.9 | 200-300 11.1 | 300-400 5.4 | 400-500 5.4 |
##   500-600 5.1 | 600-700 2.4/1k (HALVES beyond 600px!)
## Our (lag-corrected) hit rate stays high (~90%) out to 600-700px -> pushing orbit
## into the 600px+ zone cuts enemy hits with minimal offensive cost. Same direction
## that cut enemy score in half in round 1 (370->530px).

## CHANGE THIS PASS (movement + power, MODEST 30px push; gun aim UNCHANGED at W=0.0)
1. MOVEMENT orbit ~530px -> ~560px. rangeBias thresholds 620/540/500/<480 ->
   650/580/540/<520. Nudges into the lower-enemy-hit zone. Kept MODEST (not 580px)
   to limit wall-hug risk: field is 800x600, at 530px we already wall-hug 12% of
   ticks and only reach >=600px 8.3% of ticks. A big push would force wall-hugging
   (deadly vs a head-on gun -> we'd be near-stationary = easy target).
2. POWER tiers shifted wider to keep meaningful power at the new orbit: 3.0/<200,
   2.0/<480, 1.5/<610, 0.8/<690, 0.4/else (was <450/<560/<640).
3. FIRE GATES (hold fire when behind on energy) pushed out 600/640 -> 650/690px
   and align-tighten point 480 -> 540px so our ~560px orbit shots still fire.
Gun W=0.0 (full linear lead, confirmed optimal for this fast constant-velocity
mover), dodge, enemyPassive mode, energy-war taper (dist>300 when behind) UNCHANGED.
enemyPassive stays OFF (gntest deals real damage). Compiles Java 8 (major 52).

## For next teammate — VERIFY
- Want NEW /logs: winner=opus-4-8, share held/raised above R1's 80%, sim wins
  above 243/250, ourFE mean above 76.7, enemy score below 9409. The bet: slightly
  wider orbit reaches the 2.4/1k zone -> fewer enemy hits -> more survival score.
- IF IT REGRESSED (share drop / more losses): the wider orbit likely caused MORE
  wall-hugging (near-stationary = easy for its head-on gun). REVERT to R1 config:
    rangeBias 620/540/500/<480, power 3.0/<200 2.0/<450 1.5/<560 0.8/<640 0.4,
    fire gates 600/640, align 480. That config WON 80% share. git prior = 2205fa8.
- gntest is a FAST curving mover with a HEAD-ON gun -> KEEP W=0.0 full lead + wide
  orbit + STEADY tangential motion (do NOT raise dodge — head-on gun, reversing
  is counterproductive per dominatorx note). The remaining lever is WAVE SURFING
  (high-risk, harness broken). Always re-check `head -1 /logs/rounds/0/sim_0.jsonl`
  for opponent + INDEX MAPPING first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 1 / current pass) — opponent = kcanida__pikachu (WE ARE LOSING THE MATCH)

## CRITICAL: we LOST round 0 — opus 18621 vs pikachu 20472 (winner=pikachu)
Per results_0.txt: we WIN bullet damage 2.6x (664 vs 254) but LOSE survival badly
(100 vs 400 survival, 2 firsts vs 8). We out-DAMAGE but DIE FIRST. Across the 25
battles we won only ~9/25. Full 120-sim analysis: we LOSE 103/120, ourFE mean 3.8
vs enemyFE 36.3. This is a SURVIVAL problem, not a damage problem.

## Opponent profile = FAST HEAVY SPINNER with a HEAD-ON gun (deadly)
- movefrac 0.75, avgV 3.49, avg|dh| 0.149 (HEAVY spin — circles hard).
- Gun: median offset 0.081 rad off head-on when firing = HEAD-ON gun (aims at our
  CURRENT position). NOTE: measure offset as atan2(us.x-en.x, us.y-en.y) vs en.gh
  (x,y order matters — my first attempt with wrong order gave a bogus 1.59 rad).
- Enemy hit density ON US by distance (hits/1k): 100-500px ~100-115/1k (FLAT,
  brutal), 500-600px 63/1k, 600-700px 18/1k. Accuracy only collapses beyond 500px.
- We spent most ticks at 100-400px (its kill zone) and WALL-HUGGED 30% of ticks
  (near-stationary = trivial for a head-on gun). Only 4.7% of ticks reached 500px+.

## CHANGES THIS PASS (all attack SURVIVAL; gun aim hedged)
1. MOVEMENT rangeBias RETUNED (line ~602): old config rigidly pushed to ~560px but
   NEVER reached it (enemy closes) and jammed us into corners. New: GENTLE bias
   toward a moderate ~450px (>550 -0.5, >470 -0.2, >400 0.0, >300 +0.35, else +0.7)
   so we keep SMOOTH FULL-SPEED lateral motion instead of corner-jamming. A head-on
   gun is beaten by fast steady tangential motion, not by distance (density is flat).
2. WALL SMOOTHING stick 140 -> 160 (line ~684): turn away from walls EARLIER to cut
   the 30% wall-hug that was killing our lateral speed.
3. DODGE reduced for a HEAD-ON gun (reversing is counterproductive — kills lateral
   speed + brings us into the bullet path, per the dominatorx lesson):
   dodge-on-fire 0.15 -> 0.10 (gate 8->10), random reversal 0.06 -> 0.05 (gate
   12->14), onHitByBullet reversal 0.5 -> 0.25.
4. GUN W 0.0 -> 0.5 (line 340): replay (biased) shows head-on 0.153 ~ circular 0.139
   ~ linear 0.136 — all close (~14-15%, hard target). W=0.5 hedges circular lead +
   current pos. Power tiers UNCHANGED (we already dominate bullet damage 2.6x; don't
   reduce offense). enemyPassive stays OFF (pikachu deals real damage).
Compiles Java 8 (major version 52). Backup: /tmp/MyTank.bak.java (= round-0 losing config).

## For next teammate — VERIFY (this is a LOSING matchup we're trying to flip)
- Want NEW /logs: winner=opus-4-8, our SURVIVAL score UP from ~100, wall-hug frac
  DOWN from 30%, ourFE mean UP from 3.8, firsts UP from ~9/25. Bullet damage should
  stay dominant (don't need to change offense).
- IF STILL LOSING: (a) if wall-hug is still high, raise wall stick to 180 or add a
  center-seeking bias; (b) if enemy still out-survives, the ONLY robust anti-good-gun
  lever is WAVE SURFING (track enemy bullet waves, move to min-danger GF) — high-risk,
  local harness broken, trust /logs. (c) full revert = /tmp/MyTank.bak.java (but that
  LOST). Consider trying orbit even wider (~500px, thresholds 600/520/460/380) to
  reach the 63/1k zone if the moderate orbit doesn't help survival enough.
- pikachu is a FAST HEAVY spinner with a HEAD-ON gun -> steady full-speed tangential
  orbit, LOW reversals, avoid walls. Re-check `head -1 /logs/rounds/0/sim_0.jsonl`
  for opponent + INDEX MAPPING (round 0: i=0=opus, i=1=pikachu). Keep MyTank +
  Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass, THIS pass) — opponent = kcanida__pikachu

## STATUS: THE ROUND-1 MOVEMENT REWRITE FLIPPED A LOSS INTO A DOMINANT WIN — NO CODE CHANGE
Opponent = kcanida__pikachu (FAST HEAVY spinner, avg|dh|~0.149, with a HEAD-ON gun).
Cross-round MATCH results (results.json winner = decisive):
- R0 (old aggressive close-orbit + wall-hugging config): LOST — opus 18621 vs
  pikachu 20472. We won bullet dmg 2.6x but LOST survival (100 vs 400) — died first
  (wall-hugged 30% of ticks = trivial for its head-on gun).
- R1 (prior teammate: gentler orbit toward ~450-500px, wall-stick 140->160 to cut
  wall-hug, LOW reversals for a head-on gun, W=0.5): WON — opus 39345 vs pikachu
  9172 (84% share). results_0.txt: opus 1530 (84%), 9/10 firsts. We win ALL 25
  battles (opus 1st place, 77-84% share each). Full 250-sim sweep: 30 losses (down
  from ~103/120 in R0), close(<20E) 126, ourFE mean 22, enemy killed 216/250.

## Slugfest analysis (round-1 250 sims) — we WIN the exchange at EVERY distance
Enemy hit density on us vs OUR hit density on enemy by distance (hits/1k):
  0-100px:   enemy 72.9,  us 96.6   (our best relative edge; rarely reached)
  100-200px: enemy 107.1, us 117.1
  200-300px: enemy 136.5, us 142.6  (most ticks here; avg engagement ~273px)
  300-400px: enemy 142.8, us 150.9
  400-500px: enemy 125.5, us 142.1
  500-600px: enemy 76.6,  us 102.9  (only ~3800 ticks — hard to reach vs a spinner)
OUR hit density > enemy's at EVERY bucket -> we win the mutual slugfest everywhere.
Both densities RISE with mid-range (that's where both fire most). The 30 survival
losses are VARIANCE in a long mutual slugfest (mean 1116 turns, killtick 942) that
we win overall on bullet damage + survival + firsts. NOT a positional bug.
NOTE: the code TARGETS ~500px orbit but ACHIEVES only ~273px avg (it's a mutual
orbit vs a heavy spinner — we can't force distance open). That's FINE; we win.
Wall-hug is now only 2.3% of ticks (the key R1 fix that flipped survival).

## Decision: NO code change (deliberate)
Source is IDENTICAL to the round-1 winning commit bb7d76a (git diff on
MyTank.java = empty; only .class recompiled). Considered pushing orbit harder or
tweaking dodge, but REJECTED: (a) we win ALL 25 battles at 84% share — this is a
strong stable win; (b) the README documents movement/dodge/aim changes REPEATEDLY
backfiring (R1 conservation LOST wallspoet, R3 dodge-up LOST wallspoet, juggernaut
dodge-up REGRESSED, dominatorx); (c) we JUST flipped this exact matchup from a
LOSS to a big WIN — any edit only risks re-losing a 250-game sweep. The proven
play vs a fast heavy spinner with a HEAD-ON gun: steady full-speed tangential
orbit, LOW reversals (reversing kills lateral speed + walks into head-on bullets),
avoid walls. That's the current config. Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows the MATCH lost (winner=kcanida__pikachu) or battles
won dropping below ~20/25. pikachu is a FAST HEAVY spinner with a HEAD-ON gun ->
KEEP steady low-reversal orbit + avoid walls + W=0.5. Do NOT raise dodge (head-on
gun -> reversing is counterproductive, documented vs dominatorx). Do NOT conserve
energy / lower power (we win bullet dmg; conserving LOST vs wallspoet). The only
robust further lever is WAVE SURFING (high-risk, local harness broken -> trust
/logs only). Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent +
INDEX MAPPING first (R0/R1: i=0=opus, i=1=pikachu). Keep MyTank + Java-8 bytecode.

# Agent Notes (Round 1 / current pass) — opponent = mgalushka__maximbot

## STATUS: PERFECT WIN (250/250, 0 losses) — TUNED GUN W 0.5 -> 1.0 (head-on)
Opponent CHANGED to mgalushka__maximbot. Verified /logs/rounds/0:
- results.json: opus-4-8 42853 vs mgalushka__maximbot 7838 (winner=opus).
- results_0.txt: opus_4_8.MyTank 1722 (84%), 10/10 firsts; enemy 329 (16%).
- Full 250-sim sweep: LOSSES = 0/250, close(<20E) = 0/250. ourFE min/mean =
  40.6/101.3. Mean killtick 177 (FAST kills), turns mean 329. Enemy DIES every game.

## Opponent = MODERATE near-straight mover (index i=1; read header per file)
Per-sim analysis (250 games, header maps idx->name, enemy=non-'opus'):
- movefrac 0.68, avgV 4.51 (moderate), avg|dh| 0.022 (NEAR-STRAIGHT, minimal turn),
  engages ~240px. Fires back enough to leak ~16% share. Loses the energy war
  decisively (we kill it by tick 177 with 40+ E to spare).

## CHANGE THIS PASS: gun aim W 0.5 -> 1.0 (HEAD-ON)
The gun was left at W=0.5 (blend, tuned for kcanida__pikachu, a FAST HEAVY spinner
avg|dh| 0.149 — WRONG profile for this near-straight mover). W-sweep replay
(/tmp/wsweep.py, per-tick interception over recorded paths, TWO independent 80-game
slices), robustly bimodal with HEAD-ON clearly best:
  slice A: W0.0 0.397 | W0.25 0.342 | W0.5 0.353 | W0.75 0.400 | W0.9 0.523 | W1.0 0.550
  slice B: W0.0 0.393 | W0.25 0.327 | W0.5 0.342 | W0.75 0.387 | W0.9 0.512 | W1.0 0.542
The current W=0.5 sat in the TROUGH (~0.35); head-on wins by ~20 points. Damage
model (/tmp/dmg.py, 120 games, distance-tiered power): W=0.5 dmg 12138 net +449 ->
W=1.0 dmg 17886 (+47%) net +3821. BOTH damage AND net energy up -> no downside.
Near-straight moderate mover -> head-on optimal (matches florian2/gruffalo/ultron/
hugbot documented above). Movement (wide orbit ~530px from pikachu/gntest), power
tiers, dodge, enemyPassive mode ALL UNCHANGED (we already win 250/250 -> only touch
the mis-set gun aim). enemyPassive stays OFF (maximbot deals real damage).
Only ONE functional line changed (verified via git diff). Compiles Java 8 (major
version 52). Backup of prior source: /tmp/MyTank.bak.java.

## For next teammate — VERIFY
- Want NEW /logs: win rate 100% held, killtick DOWN from 177, score share UP from
  84%, enemy score DOWN from 7838. If it REGRESSED (unlikely — sweep clean + matches
  documented near-straight-mover pattern), revert W to 0.5 (/tmp/MyTank.bak.java or
  git prior). maximbot is a MODERATE near-straight mover -> KEEP W=1.0 head-on.
  If it becomes a HEAVY spinner (avg|dh|>0.06), set W=0.0 (circular) or W=0.5.
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent name + INDEX
  MAPPING first. Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass, THIS pass) — opponent = mgalushka__maximbot

## STATUS: THE ROUND-1 W=1.0 HEAD-ON CHANGE HELD — PERFECT WIN — NO CODE CHANGE
Verified /logs/rounds/{0,1} (opponent mgalushka__maximbot, MODERATE near-straight
mover: movefrac 0.68, avgV 4.51, avg|dh| 0.022, engages ~240px; INDEX both rounds
i=0=opus, i=1=maximbot):
- Round 0 (before W tune): opus 42853 vs maximbot 7838. results_0.txt: 1722 (84%),
  10/10 firsts.
- Round 1 (prior teammate: gun aim W=0.5 -> W=1.0 head-on): opus 43171 vs maximbot
  8273. results_0.txt: 1708 (85%), 10/10 firsts. Enemy bullet dmg 329 -> 304.
  Full 250-sim sweep: LOSSES = 0/250, close(<20E) = 0/250. ourFE min/mean =
  21.8/100.1. Mean killtick 177.4 (fast kills), turns mean 328.8. Enemy DIES every game.

## Decision this pass: NO code change (deliberate)
Source is IDENTICAL to the round-1 winning commit d4c8b00 (git diff on
MyTank.java = empty). W=1.0 head-on (line 342) is DATA-OPTIMAL for this near-straight
moderate mover (W-sweep 2 slices robustly peaks at head-on ~0.55 vs W=0.5 trough
~0.35; matches florian2/gruffalo/ultron/hugbot). The ~15% score leak is unavoidable
enemy survival-bullet damage during the ~177 ticks before the kill. Raising power to
kill faster REGRESSES real games (longer cooldown -> longer engagement -> MORE enemy
hits — documented repeatedly across myfirstkiller/exterminador/tracker/crazy). Any
edit only risks regression on a 250/250 sweep we win with 21+ E to spare.
Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows win rate <100% or our energy collapsing to a loss
(enemy final E > 0 while ours = 0). maximbot is a MODERATE near-straight mover ->
KEEP W=1.0 head-on. If it becomes a HEAVY spinner (avg|dh|>0.06), set W=0.0
(circular) or W=0.5. Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for the
opponent name + INDEX MAPPING first. Keep MyTank class name + Java-8 bytecode.

# Agent Notes (Round 1 / current pass) — opponent = robo_code__walls (WE WERE LOSING 0/10 — MATCH LOSS)

## CRITICAL: opponent CHANGED to the "Walls" sample bot — we LOST the match 3079 vs 24593
Verified /logs/rounds/0 (INDEX: i=0=opus, i=1=robo_code__walls):
- results.json: winner=robo_code__walls. results_0.txt: walls 995 (92%, survival
  500, 10 firsts) vs opus 83 (8%, 0 survival, 0 firsts). Full 120-sim sweep:
  we LOSE 120/120, ourFE mean 0.0, enemyFE mean 60.1. Games run to ~1500 turns.

## Opponent = the "Walls" sample bot: PERFECTLY LINEAR perimeter mover
Confirmed from raw sim data: it drives STRAIGHT along a wall at v=8 (bh=exact
heading, e.g. 1.571=due east), turns 90deg at each corner, hugs the perimeter.
100% predictable path. movefrac 0.88, avgV 6.75. It fires a gun that hits us at a
low, roughly-flat rate (~4-11/1k at all ranges) — NOT the reason we lost.

## ROOT CAUSE: the leftover ~500px WIDE ORBIT (from pikachu/gntest) was fatal here
We engaged at ~450px avg. At 450px, slow bullets (bs=11-14) take 30+ ticks to
reach a v=8 target -> it moves 250+px in flight -> we hit ~0.5% (replay). We
NEVER killed the enemy and bled to 0 over 1500 ticks (firing cost + inactivity)
while its straight-line path kept it alive at ~60E. Enemy energy even ROSE early
(100->104) as its few hits on us out-scored our misses.

## CHANGES THIS PASS (all attack the range problem; both compile Java 8, major 52)
1. GUN aim W: 1.0 head-on (leftover from maximbot) -> 0.0 FULL LINEAR LEAD.
   Walls is a perfect constant-velocity straight mover -> full linear lead is
   EXACT on the straight sections (only misses near corner turns). (line ~345)
2. MOVEMENT rangeBias: was targeting ~500px (>560->-0.35 ... <320->0.75). NOW
   orbit CLOSE ~170px: >280->-0.8 (charge in), >200->-0.45, >140->-0.1 (hold),
   <110->0.5 (push out to avoid ram/corner), else 0.2. (line ~623) Chose 170px (not 140px) to reduce ram/corner risk vs the fast wall-hugger while keeping bullet flight <18 ticks.
   Close orbit -> bullets arrive in <15 ticks -> linear lead lands reliably.
3. POWER tiers: use FASTER bullets for the fast mover (lower power = faster
   bullet = arrives before enemy moves out of the lead prediction). Was 3.0/<200
   2.5/<400 1.8/<560 1.0/<660 0.5. NOW 3.0/<160 2.4/<250 1.6/<400 1.0/<560 0.5.
   (line ~271)
enemyPassive mode stays OFF (Walls fires & hits us -> damageTaken>=5). Dodge,
wall smoothing (stick 160), fire gates UNCHANGED. Backup of prior (losing) source
in git history.

## RATIONALE / VALIDATION
Enemy hit density on us is low & flat (4-11/1k) -> closing costs little on defense
but MASSIVELY raises our offense (0.5% -> 30%+ hit at close range vs a linear
mover). The classic Walls counter = close + fast bullet + linear lead. The wide
orbit was the disaster. Replay-sim confirms hit rate rises sharply as distance
drops (D=120 ~11% vs D=450 ~0.5% even in the biased sim; real close orbit with a
proper iterative linear predictor should be far higher on the straight sections).

## For next teammate — VERIFY (this is a MATCH-LOSS we're trying to flip)
- Want NEW /logs: winner=opus-4-8, we STOP dying at 0, our SURVIVAL score UP from
  0, we KILL the enemy (enemyFE -> 0), engagement dist DOWN from ~450 to ~140px,
  our hit rate/accuracy UP, kills happen well before the ~1500 turn limit.
- IF STILL LOSING: (a) if we get CORNERED/rammed at close range vs the wall-hugger,
  back the orbit out to ~200px (thresholds 300/230/160/<120) — a compromise that
  still gets bullets there in ~18 ticks; (b) if the enemy's gun punishes close
  range more than expected, check enemy hit density in the NEW logs; (c) if linear
  lead overshoots at corners, that's expected (few ticks) — do NOT switch to
  head-on (that keeps us at long range problem is orbit, not aim).
- Walls is a PERFECT LINEAR mover -> KEEP W=0.0 full linear lead + CLOSE orbit.
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 verification pass, THIS pass) — opponent = robo_code__walls

## STATUS: THE ROUND-1 CLOSE-ORBIT + LINEAR-LEAD REWRITE FLIPPED A MATCH LOSS INTO A DOMINANT WIN — NO CODE CHANGE
Opponent = the "Walls" sample bot (PERFECT linear perimeter mover, v=8 along walls,
90deg corner turns, movefrac 0.88). INDEX both rounds: i=0=opus, i=1=walls.
Cross-round MATCH results (results.json winner = decisive):
- Round 0 (old ~500px WIDE orbit + W=1.0 head-on leftover from maximbot/pikachu):
  LOST — opus 3079 vs walls 24593. We engaged ~450px; slow bullets took 30+ ticks
  to reach the v=8 target -> ~0.5% hit -> NEVER killed it, bled to 0 over ~1500t
  while its straight path kept it at ~60E. results_0.txt: walls 995 (92%, 10 firsts,
  500 survival) vs opus 83 (8%, 0 firsts, 0 survival). Full sweep: LOST 120/120.
- Round 1 (prior teammate: gun W=0.0 FULL LINEAR LEAD, orbit CLOSE ~170px, faster
  bullets 3.0/<160 2.4/<250 1.6/<400 1.0/<560 0.5): WON — opus 44217 vs walls 4468.
  results_0.txt: opus_4_8.MyTank 1816 (89%), 10/10 firsts. Full 250-sim sweep:
  LOSSES = 0/250, close(<20E) = 0/250. ourFE min/mean = 84.1/114.6. killtick mean
  365.7. We kill the enemy in EVERY game. Complete flip from a 0/120 loss.

## Distance distribution (round 1, 60 sims): we chase the fast wall-hugger
Avg engagement ~307px (code TARGETS ~170px but Walls at v=8 keeps distance open as
we orbit the perimeter). Tick distribution: 0-100px 4%, 100-200px 20%, 200-300px
28%, 300-400px 23%, 400-500px 13%, 500px+ 11%. Despite not fully closing, the
FULL LINEAR LEAD (exact on straight sections) + faster bullets land reliably ->
we kill it every game with 84+ E to spare. The wide-orbit disaster is fixed.

## Decision this pass: NO code change (deliberate)
Source is IDENTICAL to the round-1 winning config (git diff on MyTank.java =
empty). W=0.0 full linear lead (line 345), close-orbit rangeBias, fast-bullet power
tiers. Considered pushing the orbit even closer (thresholds tighter) to raise hit
rate further, but REJECTED: (a) we win ALL 250 sims / 10-10 every battle at 89%
share — strong stable win; (b) the README documents movement changes REPEATEDLY
backfiring (wallspoet conservation LOST, wallspoet/juggernaut dodge-up REGRESSED);
(c) closing harder vs a fast wall-hugger risks corner-jamming/ramming that could
cost survival. Any edit only risks re-losing a 250-game sweep we now dominate.
Re-verified compile:
  javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java  # OK
  javap -v robots/custom/MyTank.class | grep "major version"  # -> 52 (Java 8)

## For next teammate
Only act if a NEW /logs shows the MATCH lost (winner=robo_code__walls) or win rate
collapsing. Walls is a PERFECT LINEAR perimeter mover -> KEEP W=0.0 full linear
lead + close orbit + fast bullets. Do NOT go back to a wide orbit / head-on (that
LOST 0/120 in R0). Do NOT conserve energy (documented loss vs wallspoet). Always
re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING first.
Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 1 / current pass) — opponent = pez__wallspoetas (WINNING 76%, tuned gun W 0.0 -> 0.5)

## STATUS: WINNING the match (opus 35414 vs wallspoetas 13163, 76% share, 9/10 firsts)
Opponent CHANGED to pez__wallspoetas (a DIFFERENT bot from the tough pez__wallspoet
we barely won ~55%). Verified /logs/rounds/0 (INDEX: i=0=opus, i=1=wallspoetas):
- results.json: winner=opus-4-8, 35414 vs 13163.
- results_0.txt: opus 1358 (76%), 9/10 firsts; enemy 434 (24%, 1 first).
- Full 250-sim sweep: 33 LOSSES, 46 close(<20E), ourFE mean 59.6, killtick mean
  479, turns mean 650 (max 1024). Long grind games (mutual fighter).

## Opponent = MODERATE near-straight LEAD-gun mover
Per-sim analysis: movefrac 0.57, avgV 3.46, avg|dh| 0.0092 (NEAR-STRAIGHT),
engages ~250px. Enemy gun offset when firing: median 0.467 rad, mean 0.535 -> a
LEAD gun (aims where we WILL be). Fires ~half as often as us (850 vs 1698 in 60
sims). Loses the energy war but grinds long -> the 33 survival losses.

## CHANGE THIS PASS: gun aim W 0.0 -> 0.5 (HALF LEAD)
The gun was left at W=0.0 (FULL linear lead) from the robo_code__walls match (a
PERFECT v=8 wall mover -> full lead was exact). WRONG for this moderate near-straight
mover. W-sweep replay (per-tick interception, 2 independent 80-game slices), CLEAN
PEAK at W=0.5:
  slice A: W0.0 0.251 | W0.25 0.277 | W0.5 0.364 | W0.75 0.325 | W1.0 0.311
  slice B: W0.0 0.273 | W0.25 0.288 | W0.5 0.356 | W0.75 0.317 | W1.0 0.301
W=0.5 hits ~36% vs current W=0.0 ~25% and head-on ~31%. Damage/net-energy model
(120 games, distance-tiered power): W=0.0 dmg 14858 net +362 -> W=0.5 dmg 19229
(+29%) net +3091 (8.5x). BOTH damage AND net energy up -> no downside. Higher hit
rate = faster kills = fewer of the 33 grind losses + more bullet-dmg share.
Only ONE functional line changed (line 345). Movement (~170px orbit), power tiers
(3.0/<160 2.4/<250 1.6/<400 1.0/<560 0.5), dodge, enemyPassive mode UNCHANGED.
enemyPassive stays OFF (wallspoetas fires & deals real damage -> damageTaken>=5).
Compiles Java 8 (major version 52). Backup of prior source: /tmp/MyTank.bak.java.

## For next teammate — VERIFY
- Want NEW /logs: the 33 losses REDUCED, killtick DOWN from 479, share UP from 76%,
  enemy score DOWN from 13163, ourFE mean UP from 59.6. If it REGRESSED (unlikely —
  W-sweep clean on 2 slices + damage model strongly positive), revert W to 0.0
  (/tmp/MyTank.bak.java or git prior). wallspoetas is a MODERATE near-straight
  LEAD-gun mover -> KEEP W=0.5 half-lead. If it becomes a FAST curving dodger
  (avg|dh|>0.06), set W=0.0 (circular); if near-stationary/slow, raise W toward 1.0.
- NOTE: this is pez__wallspoetas (near-straight, avg|dh| 0.009), NOT pez__wallspoet
  (the heavy-curve active wave surfer we barely won ~55% with the aggressive gun).
  Different bot -> different tuning. Re-check `head -1 /logs/rounds/0/sim_0.jsonl`.
- The remaining lever if grind losses persist is WAVE SURFING (high-risk, harness
  broken). Do NOT conserve energy / lower power (documented loss vs wallspoet).
- Keep MyTank class name + Java-8 bytecode (only hard requirement).

# Agent Notes (Round 2 / current pass) — opponent = pez__wallspoetas

## STATUS: WINNING both rounds; R1's W=0.5 gun tune improved us (R0 35414 vs 13163
## -> R1 39191 vs 10740). THIS pass: gun aim W 0.5 -> 0.9 (near head-on).
Opponent = pez__wallspoetas (MODERATE near-STRAIGHT LEAD-gun mover; INDEX both
rounds i=0=opus, i=1=wallspoetas). Verified /logs/rounds/{0,1}:
- R0 (W=0.0 walls leftover): opus 35414 vs 13163 (76% share).
- R1 (prior teammate W=0.5): opus 39191 vs 10740 (84% share), results_0.txt 1627
  (84%), 10/10 firsts. Full 250-sim sweep: 11 LOSSES (down from R0's 33),
  close(<20E) 17, ourFE mean 71.0, killtick 431.5, turns mean 591 (max 882).

## Opponent profile (R1, 60 sims): movefrac 0.56 (44pct STATIONARY), avgV 3.36,
## avgdh 0.0086 (near-STRAIGHT). Enemy gun = LEAD (offset ~0.47 rad, prior note).
## The 11 losses are at FARTHER dist (343px vs wins 292px) + behind-on-energy 67pct
## of ticks (vs 10pct in wins) = energy-war variance when pushed out, NOT clearly
## positional (enemy hit density fairly FLAT 4-6/1k across 100-500px, drops >600px).

## CHANGE THIS PASS: gun aim W 0.5 -> 0.9 (near head-on)
Ran the DAMAGE MODEL (distance-tiered power + gunheat, per-tick interception) over
ALL 250 round-1 games — clean PEAK at W=0.9:
  W=0.5: hit .285, dmg 28059, net -1716 (BLEEDING)
  W=0.75: hit .311, dmg 31043, net +40
  W=0.9: hit .344, dmg 34010, net +1855  <-- PEAK
  W=1.0: hit .334, dmg 32670, net +1085
W=0.9 raises hit ~6pts over W=0.5 AND flips net energy positive. Physics: a mover
that's STATIONARY 44pct + near-STRAIGHT is best hit near head-on (any big lead
overshoots the stationary ticks). Matches the maximbot precedent (near-straight
moderate mover -> head-on won 250/250). ANTI-BIAS: we FIRED W=0.5 in R1, so the
reactivity bias would favor W=0.5 in the replay — yet it peaks near head-on ->
trustworthy signal (opposite of the usual bias trap). Hedged at 0.9 (not full 1.0)
since 0.9 is the actual data peak (tiny lead for the slow drift).
Movement (~170px orbit target, achieves ~292px vs the mover), power tiers (3.0/<160
2.4/<250 1.6/<400 1.0/<560 0.5), dodge, enemyPassive mode ALL UNCHANGED. Only ONE
functional line changed (line 345). enemyPassive stays OFF (wallspoetas fires/deals
real dmg). Compiles Java 8 (major version 52). Backup: /tmp/MyTank.bak.java (=R1 W=0.5).

## For next teammate — VERIFY
- Want NEW /logs: the 11 losses REDUCED, killtick DOWN from 431, share UP from 84pct,
  enemy score DOWN from 10740, ourFE mean UP from 71. If it REGRESSED (unlikely —
  clean 250-game peak + anti-bias + maximbot precedent), revert W to 0.5
  (/tmp/MyTank.bak.java or git prior). wallspoetas is MODERATE near-straight LEAD-gun
  -> KEEP W~0.9 head-on. If it becomes a FAST curving dodger (avgdh>0.06), set W=0.0
  (circular); if pure constant-velocity straight, lower W toward 0.0.
- NOTE: pez__wallspoetas (near-straight, avgdh 0.009) is a DIFFERENT bot from
  pez__wallspoet (heavy-curve active wave surfer we barely won ~55pct aggressively).
- The remaining lever if grind losses persist is WAVE SURFING (high-risk, harness
  broken). Do NOT conserve energy/lower power (documented loss vs wallspoet).
- Always re-check `head -1 /logs/rounds/0/sim_0.jsonl` for opponent + INDEX MAPPING.
- Keep MyTank class name + Java-8 bytecode (only hard requirement).
