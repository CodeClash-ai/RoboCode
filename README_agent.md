# Agent notes for Robocode teammates

Round 1 replaced the starter `Robot` (simple ahead/fire) with `custom.MyTank` as an `AdvancedRobot`.

Round 2 changes:
- Kept the successful radar-lock/orbit architecture.
- Added circular predictive targeting: it estimates enemy turn rate from consecutive scans and falls back to linear prediction when the turn rate is near zero.
- Reworked movement to use radians and a small wall-smoothing loop instead of a one-shot flip, with a preferred distance around 410.
- Added more irregular reversals and `onBulletHit` energy bookkeeping.

Current strategy in `robots/custom/MyTank.java`:
- independent radar lock (`setAdjustGunForRobotTurn`, `setAdjustRadarForGunTurn`)
- circular/linear predictive targeting with field clamping
- perpendicular orbit movement with distance control and wall smoothing
- reverses direction on detected enemy energy drop (likely fire), bullet hits, wall hits, and close/long range
- fires stronger up close, conserves energy when low/far

Code compiles with:

```bash
javac -cp libs/robocode.jar robots/custom/MyTank.java
```

Local `./robocode.sh -battle ...` still is not useful in this stripped workspace: sample/opponent robots are absent and Robocode prints `Can't find ...` even though it creates database entries for `custom.MyTank`. The actual game harness has been loading our bot directly (see `/logs/rounds/*/sim_*.jsonl`).

Log observations:
- Round 0 and 1 results both show `gpt-5-5` as winner. The sim logs only contain our robot, meaning the opponent appears invalid/not spawned in those harness runs; keep code valid and robust in case this changes in later rounds.

Potential next improvements:
- If future logs show real enemy movement and many misses, add a small guess-factor/statistical gun or choose between head-on/linear/circular based on recent hit rate.
- If wall collisions appear, tune `WALL_MARGIN`/`wallSmooth` projection distance.

Round 3 note (current edit):
- Observed real opponent in `/logs/rounds/0`: `wouterjoosse__infinitylock` is stationary but continuously radar/gun locks and fires. Our prior bot won all recorded matches, but some traces showed our movement getting stuck on walls/corners for many ticks while still firing.
- Changed `robots/custom/MyTank.java` movement defensively:
  - added a cooldown for range-triggered random reversals so close/far scans cannot flip direction every tick;
  - strengthened wall smoothing by trying both smoothing directions, then falling back to a center-field escape angle;
  - when already near an edge, prioritizes driving toward the battlefield center;
  - `onHitWall` now drives toward center instead of a fixed `setBack`/turn that could keep us pinned.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 current update:
- Reviewed `/logs/rounds/0`: every result file was a 10/10 win, with max normal bullet/survival score (1800 per 10-round match) against `wouterjoosse__infinitylock.MyTank`.
- The opponent in traces remains stationary (velocity 0, fixed body/gun heading) and does not appear to fire; our bot kills it reliably with predictive shots.
- Added `stationaryScans` detection in `robots/custom/MyTank.java`. If the enemy has been motionless for >5 scans, the gun now uses power 3.0 out to range 680 while energy is safe, reducing kill time/exposure against infinitylock/SittingDuck-style opponents. Moving enemies still use the previous conservative power ladder.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (this handoff) note:
- Current `/logs/rounds/0` opponent is `robo_code__sittingduck` (stationary; no movement, no fire in sampled traces). All 25 recorded result files are 10/10 wins for us with 1800 score per 10-round battle.
- Added `tools/analyze_rounds.py` to summarize future `/logs/rounds` results and trace movement/firing clues.
- Tiny gun tweak: stationary-target mode now uses power 3.0 at any range while our energy is >12 (removed old `<680` range cap). Against SittingDuck-style bots, prediction is exact and stronger bullets should finish faster; moving enemies still use the old power ladder.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit):
- Logs still show 50/50 battle wins versus stationary `robo_code__sittingduck`; opponent never moves and effectively never fires in normal traces.
- Added an `enemyFireCount` guard: after >10 scans of a stationary non-firing enemy, `MyTank` now stops (or first moves inward if near a wall) while keeping radar/gun lock and firing power 3. This should reduce self-inflicted wall travel/time against SittingDuck-style bots; if a stationary enemy does show an energy-drop shot, normal orbit/dodge remains enabled.
- Fixed `onHitByBullet` dodge math to use the bullet bearing relative to our current heading (`bearing + PI/2`) instead of an absolute-ish angle; bullet/wall/robot hit handlers also restore max velocity in case we were in stationary-farming mode.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `it_economics__ite_bomax`):
- New `/logs/rounds/0` shows a real but weak opponent, `it_economics__ite_bomax.MyTank`: valid bot, max velocity about 3.0-3.5, stop-and-go movement in a small area, repeatedly fires power-3 bullets mostly in a fixed/poor direction. We won all 25 ten-round battles; total score 37215 vs 176, average battle score about 1489 vs 7.
- Kept the existing orbit/radar/circular gun, but added slow-target tracking in `robots/custom/MyTank.java`:
  - `slowEnemyScans` plus exponential averages of enemy velocity/turn rate;
  - after a target has stayed <= speed 3.25 for >12 scans, prefer closer orbit distance (335) to increase hit rate and kill speed;
  - use power 3.0 against confirmed slow targets within range 560 while energy is safe;
  - for slow stop-and-go targets, aim with blended velocity/turn-rate averages instead of trusting the current tick's zero/burst velocity.
- A quick offline check over trace positions suggested the blended slow-target predictor reduces approximate future-position error versus the old instantaneous predictor (mean ~23.2 -> ~21.0 px on sampled shots). Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit follow-up):
- Reviewed `/logs/rounds/1`: still 250/250 game wins versus `it_economics__ite_bomax`; average score improved to 38874 vs 232, but trace shows our bot still touches walls about 1.2 times/game while the opponent is weak/slow and inaccurate.
- Made a small anti-Bomax tuning pass in `robots/custom/MyTank.java`:
  - increased wall margin from 42 to 58 and wall-smoothing projection from 155 to 190 to start bending away from edges earlier;
  - for confirmed slow enemies, tightened preferred orbit distance from 335 to 285 (keeps us nearer the small stop-and-go target for faster/high-power hits instead of wide wall-spanning laps);
  - expanded slow-target power-3 mode to trigger after >8 slow scans, at energy >12, and out to range 720. This opponent moves <=3.5 and our hit rate is ~60%, so max-power bullets should shorten rounds/raise bullet bonus.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `trex22__deepthought`):
- `/logs/rounds/0` shows a real moving opponent, `trex22__deepthought.MyTank`. We still won all 25 ten-round battles (results total 43414 vs 261; avg per battle ~1737), but rounds were longer than against stationary bots and traces suggest the opponent's stop/reverse movement makes our old pure circular/linear predictor over-lead.
- Added lightweight virtual-gun selection in `robots/custom/MyTank.java`:
  - tracks rolling virtual-wave errors for head-on, linear, circular, and averaged slow/stop-go predictors;
  - starts with the old circular/averaged defaults, then chooses the lowest-error gun after enough samples;
  - keeps stationary-target forcing to head-on/max-power, preserving SittingDuck/infinitylock farming behavior.
- Offline replay approximation over the DeepThought traces showed head-on had lower future-position error than circular/linear for this opponent, so the virtual gun should adapt toward it while remaining generic for future opponents.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `trex22__deepthought`, follow-up):
- Reviewed `/logs/rounds/1`: still 250/250 game wins, total 43576 vs 269. Average round length improved from ~558 to ~529 ticks after the previous virtual-gun change.
- Offline replay over our actual fire ticks in `/logs/rounds/1` showed head-on prediction is still best for DeepThought (mean future-position error ~51 px vs ~71-84 for averaged/linear/circular at the actual bullet powers; head-on especially dominates when the target bursts at max speed then reverses/stops).
- Tuned `robots/custom/MyTank.java` to exploit this once virtual guns confirm it:
  - `chooseGun()` now starts with head-on rather than circular during the cold-start period (stationary-target behavior is unchanged);
  - added `headOnGunIsBest()` and, when true, uses a closer 330px preferred orbit distance plus heavier bullet powers out to 720px while energy is safe;
  - moving/slow/stationary fallback logic remains in place for future opponents where head-on is not winning the virtual-gun scores.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.


Round 1 (gpt-5-5 current edit against `pez__gf1`):
- `/logs/rounds/0` is a much harder real opponent, `pez__gf1.MyTank` (GF1-style movement/gun): over 250 games we won 246, lost 1, drew 3. Opponent hits almost never according to traces, but it surfs/runs well; our accuracy was only ~14%, average score 26087 vs 178, with rare long games where our bot spent itself down to zero.
- Added a lightweight guess-factor virtual gun to `robots/custom/MyTank.java` alongside head-on/linear/circular/averaged guns. It learns enemy lateral escape offsets from the same virtual waves and can be selected by the existing rolling-error gun chooser. Stationary and slow-target special cases remain intact.
- Added a conservative power guard for hard-to-hit moving enemies: if virtual guns all show large error and energy is falling, cap bullet power more aggressively (down to 1.25/0.55 tiers) to avoid rare self-depletion losses/draws against GF-style surfers.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `pez__gf1`, follow-up 2):
- Reviewed `/logs/rounds/1`: still first in every 10-round battle, but actual game wins slipped to 245/250 with 3 GF1 wins and 2 draws; total score dropped vs previous logs (24917 vs 327). The GF virtual gun addition appeared too optimistic/noisy for this surfer, and rare losses happen when we spend ourselves to zero in very long games.
- Tuned `robots/custom/MyTank.java` conservatively for hard-to-hit moving enemies:
  - de-biased the new guess-factor virtual gun at startup (initial rolling error 70 instead of 54) and require >45 samples plus an 8px clear error margin before it can be selected;
  - for high virtual-gun error movers, tighten preferred distance to ~355 to reduce bullet flight time, but keep outside ram range;
  - low-energy hard-to-hit mode now fires tiny 0.15 bullets instead of stopping fire entirely/heavy spraying, so misses drain slowly and hits are energy-positive;
  - tightened the gun-alignment tolerance for hard-to-hit movers to avoid wasting shots while still farming stationary/slow targets as before.
- Added `tools/offline_gun_eval.py`, a rough trace replay helper that compares head-on/linear/circular/averaged prediction errors from `/logs/rounds/*/sim_*.jsonl`. It suggested averaged/head-on still beat instantaneous linear/circular on GF1 traces; the actual guess-factor gun was not validated by this replay.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `linuxuser0__genetic`):
- `/logs/rounds/0` opponent is a real but weak wall-hugging genetic bot. We won all 25 ten-round battles, total 44670 vs 225; average score ~1787/1800 and no game losses. Opponent moves at max speed sometimes but spends a lot of time clipped to battlefield edges, fires only ~2 detectable bullets/game, and scores only small bullet damage.
- Offline replay (`tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'`) showed head-on/damped prediction beats full linear/circular for this opponent (full predictors over-lead wall stops/reverses). Added `wallEnemyScans` in `robots/custom/MyTank.java` to detect targets within 44px of an edge.
- When the enemy is wall-bound for several scans, the bot now uses a closer ~305px orbit, max power (energy permitting), and the averaged gun with a special damped-linear projection. Stationary/slow/GF safeguards from prior rounds remain unchanged.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.


Round 2 (gpt-5-5 current edit against `linuxuser0__genetic`, follow-up):
- `/logs/rounds/1` still shows 250/250 game wins and a small score gain (44698 vs 211). Opponent remains a wall/edge-heavy bot that usually fires weak power-1 shots, occasionally with decent aim.
- Made a modest wall-target detection retune in `robots/custom/MyTank.java`:
  - enemy wall-bound detection margin widened from 44px to 70px and confirmation threshold reduced from >6 to >4 scans, so the damped wall predictor/max-power mode engages earlier during edge slides;
  - wall-bound max-power mode now allows range <820 at energy >14;
  - kept wall-bound orbit at a safer ~315px (closer than generic but not too close to its weak head-on gun). A brief idea to go 275px was rejected because trace stats showed its hit distance averages ~318px and only ~20% of its shots occur below 300px.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.
- Also fixed `tools/offline_gun_eval.py` to identify our robot by name instead of assuming id 0; the harness sometimes swaps robot ids. Corrected replay on `/logs/rounds/1` says wall-damped/head-on predictors are best for this opponent (wallavg mean ~52px, head-on ~53px, normal avg ~59px, linear/circular ~74px).

Round 1 (gpt-5-5 current edit against `kinnla__antiwalls`):
- `/logs/rounds/0` shows `kinnla__antiwalls.MyTank`, an almost harmless anti-wall mover: spends most ticks stopped, occasionally moves in long straight wall/edge-aligned bursts at max speed, and effectively never scores bullet damage. We won all 250 games; average 1796/1800 score per 10-round battle (`results.json` total 45039 vs 20), 71% hit rate, no wall/rams.
- Ran `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'`: full linear/circular prediction was best on this opponent (mean future error ~8px) because it moves in straight bursts; the prior wall-bound forcing used the damped averaged wall predictor (~12px) and could over-dampen those bursts.
- Small code tweak in `robots/custom/MyTank.java`: wall-bound targets now use `GUN_LINEAR` immediately when the scan shows a fast, straight wall burst; otherwise they only force the damped `GUN_AVERAGED` predictor during the first 18 virtual-gun samples. After that, the existing virtual-gun chooser is allowed to select linear/circular/head-on based on measured error. This preserves cold-start behavior for previous wall-stop bots but adapts better to antiwalls-style straight edge slides.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.
