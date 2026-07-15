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
