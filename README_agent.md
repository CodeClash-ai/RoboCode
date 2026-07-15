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

Round 2 (gpt-5-5 current edit against `kinnla__antiwalls`, follow-up):
- `/logs/rounds/1` remained a clean 250/250 game sweep, total 45050 vs 84. Average score is essentially max (1802/10-round battle including small ram points). The only opponent score came from rare point-blank spawn shots/collisions; sampled enemy fire events were at ~50-175px and only 3 actual bullet hits across 250 traces.
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` still says full linear/circular prediction is best for this opponent (mean error ~8.4px vs head-on ~16.1, damped wallavg ~13.2). Some straight runs occur away from the wall, so relying only on `wallEnemyScans` can cold-start the wrong gun.
- Updated `robots/custom/MyTank.java`:
  - added `straightEnemyScans` for sustained non-turning movement;
  - confirmed harmless straight runners (no detected enemy fire) now use closer ~305px orbit, max power out to 760, and force `GUN_LINEAR` early even if not wall-bound;
  - if spawned/dragged inside 118px of a non-firing enemy, drive directly away before resuming orbit to reduce rare point-blank bullet/ram leakage.
- Guarded the new straight-run overfit with `enemyFireCount == 0` so real firing surfers/random movers still rely on the virtual-gun chooser and existing conservative power logic.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `barriosnahuel__tirolio`):
- `/logs/rounds/0` shows another harmless mover, `barriosnahuel__tirolio.MyTank`: it moves at up to max speed across much of the field but almost never/never fires. We swept all 250 games, total score `45020` vs `39`; the opponent's points are only tiny ram/collision leakage.
- Ran `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'`. Unlike the previous antiwalls opponent, the damped averaged predictor is best on these traces (mean error ~64px) while full linear/circular are worse (~87px) and head-on is much worse (~125px).
- Small retune in `robots/custom/MyTank.java`: the harmless straight-run / wall-straight branches still cold-start `GUN_LINEAR`, preserving the prior antiwalls behavior, but after ~22 virtual-gun samples they only force linear if its rolling error remains within 5px of the averaged gun. This lets the virtual gun chooser switch to averaged for Tirolio-style stop/reverse/random wall movement.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `barriosnahuel__tirolio`, follow-up):
- Reviewed `/logs/rounds/1`: still swept 250/250 games. Score stayed near max (`44992` vs `12`), average round length improved to ~538 turns and accuracy to ~31%, but Tirolio remains a harmless mover with many short straight-looking segments that stop/reverse/wall-bounce unpredictably.
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` again shows the damped averaged predictor best for this opponent (`avg` mean error ~66px vs linear/circular ~86px, head-on ~124px). A separate shot-time replay approximation also favored averaged (~67px vs linear ~92px).
- Small targeted tweak in `robots/custom/MyTank.java`:
  - for harmless straight motion that is *not* wall-bound, force `GUN_AVERAGED` instead of cold-starting/forcing `GUN_LINEAR`; the wall-bound straight branch still preserves antiwalls-style linear edge-slide behavior;
  - tightened harmless straight-run orbit from ~305px to ~275px to shorten bullet flight against this effectively non-firing opponent.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `pez__droidpoet`):
- `/logs/rounds/0` is a much tougher active wall/perimeter runner. We still won the aggregate match (`results.json` 28450 vs 10378 and first in every 10-round result file), but actual per-game survival was poor: trace winners show us alive only 98/250 games while DroidPoet survived 152/250. Our total score lead came from much higher bullet damage/bonuses, but we were often spending ourselves to zero with max-power shots.
- Trace observations: opponent is wall-bound about 97% of our shot opportunities, moves max speed around the edges, and fires frequent power-1.2 bullets. `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` says normal averaged prediction is best on our actual shots (avg mean error ~109px; linear/circular ~120; wall-damped ~128; head-on ~151), so the old harmless-wall max-power/wall-damped special case was counterproductive here.
- Code changes in `robots/custom/MyTank.java`:
  - added `dangerousWallEnemy()` detection for wall-bound targets that have fired several times; it activates early (wall scans >4 and enemy fire count >3);
  - dangerous wall runners now use a wider ~470px preferred orbit instead of the close 315/355px wall/GF orbit, to reduce hits from the simple power-1.2 gun;
  - they no longer trigger the old wall max-power branch, cap bullet power to about 1.35-1.85 (2.15 only very close), and force the normal `GUN_AVERAGED` predictor instead of wall-damped/head-on/linear. This is intended to improve survival and avoid energy depletion while keeping the gun choice favored by trace replay.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `pez__droidpoet`, follow-up):
- Reviewed `/logs/rounds/1`: the prior "dangerous wall runner" survival tune backfired. We still won aggregate (24574 vs 13284) but only 23/25 ten-round battle files were first place and survival got worse; DroidPoet survived 163/250 traced games. Per-result totals show our bullet damage dropped from 19139 in round 0 to 17561 while opponent bullet damage rose (2047 -> 3021), so the wider 470px orbit/1.35-power cap was giving it too many long rounds.
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` still favors the normal averaged predictor for this active wall runner (avg mean error ~100px vs linear/circular ~109, wall-damped ~119, head-on ~140), so I kept the `dangerousWallEnemy()` gun override to `GUN_AVERAGED`.
- Retuned only the dangerous-wall movement/power in `robots/custom/MyTank.java`:
  - preferred orbit distance is back near the earlier aggressive wall distance (335 instead of 470) to shorten bullet flight and improve our hit/damage rate;
  - dangerous wall enemies now get high pressure while we have energy (power 3 under ~520px, 2.35 farther out to 760), but still downshift to 0.45 below 10 energy and moderate 1.65-2.1 in the mid-low energy band to avoid self-depletion.
- This is a partial rollback toward the higher-scoring round-0 behavior while preserving the useful averaged-gun detection for DroidPoet. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__crazy`):
- `/logs/rounds/0` opponent is Robocode sample.Crazy-like: high-speed constant turning, frequent weak power-1 firing. We swept 250/250 games (`results.json` 40098 vs 799), but average score was only ~1604/10-round battle and rounds lasted ~540 ticks because many max-power shots missed.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` showed circular prediction is best on our actual shots (circ mean future error ~128px vs avg ~137, linear ~145, head-on ~162). A quick fixed-power replay suggested faster moderate bullets would reduce lead error substantially compared with power-3 against this fast turner.
- Updated `robots/custom/MyTank.java` with `crazyEnemyScans` detection (velocity >5.2 and heading turn >0.035 rad/tick while not persistently wall-bound). When confirmed, it forces `GUN_CIRCULAR`, uses a ~340px orbit, and caps bullet power to about 1.75-2.45 while energy is healthy (lower when energy is low). This should improve hit rate/score against Crazy without affecting stationary/slow/wall-special cases unless the high-speed-turning signature appears.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `robo_code__crazy`, follow-up):
- Reviewed `/logs/rounds/1`: the previous Crazy-specific circular/moderate-power tune improved the sweep score from `40098 vs 799` to `41125 vs 529`, with average round length down from ~540 to ~450 ticks. We still won all 250 traced games.
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` now shows circular prediction clearly best on actual shots (circ mean ~110px vs avg ~120, linear ~128, head-on ~147), and a quick lateral-error replay suggests closer/faster circular shots should improve hit rate versus this high-speed turner.
- Made a small follow-up in `robots/custom/MyTank.java`: Crazy signature now engages after >4 scans instead of >8, uses a tighter ~305px orbit, and raises the healthy-energy cap slightly (about 1.85-2.5 power depending on range). This is meant to shorten bullet flight/rounds while keeping the anti-self-depletion caps at low energy.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `it_economics__ite_claptrap`):
- `/logs/rounds/0` shows a valid but harmless high-speed mover, `it_economics__ite_claptrap.MyTank`. We swept 250/250 games (`results.json` 44812 vs 66); opponent energy-drop shot detection is near zero and its score is only occasional tiny bullet/ram leakage. Average traced round length is ~500 ticks.
- Opponent behavior: about 21% stopped, ~51% near walls, ~77% straight-motion ticks; long straight runs are common (median straight run ~34 ticks), but the bot also stops/reverses/wall-bounces.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` says linear/circular are best overall on all future-position samples (`lin/circ ~78px`, avg ~79px, head ~137px). However a rough actual-shot-time replay showed averaged slightly better on sustained non-wall straight shots (avg ~57px vs lin ~62px), so I did **not** change the existing non-wall straight gun away from `GUN_AVERAGED`.
- Small code tweak in `robots/custom/MyTank.java`: for sustained non-wall straight, non-firing targets (`straightEnemyScans > 16`, `wallEnemyScans <= 4`), use a slightly wider ~310px orbit instead of the generic harmless 275px orbit to reduce rare ram/point-blank leakage while keeping short bullet flight, and keep high bullet power (3.0 under ~620, 2.65 farther) while energy is safe. Existing wall-straight linear branch and averaged non-wall straight gun remain intact.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `it_economics__ite_claptrap`, follow-up):
- `/logs/rounds/1` remained a 250/250 sweep (`results.json` 44813 vs 110), avg length ~508 turns, accuracy ~34%. Claptrap is still a mostly harmless max-speed mover with rare low-power/stray shots; opponent score is only minor leakage.
- Re-ran `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'`: overall linear/circular are best (~78.8px mean error), while category replay shows non-wall straight shots favor averaged (~48px vs linear ~55) but wall-straight shots favor linear (~87px vs averaged ~93). This validates keeping averaged for non-wall straight but strengthening the wall-straight linear override.
- Code tweaks in `robots/custom/MyTank.java`:
  - wall-bound straight runners now keep the `GUN_LINEAR` override longer (`virtualSamples < 45`) and allow an 8px margin vs averaged, improving Claptrap/Antiwalls-style edge runs without permanently forcing linear if virtual scores clearly reject it;
  - added `harmlessLowFireEnemy()` (`enemyFireCount <= 2`) and use it for aggressive straight-run orbit/power/gun branches. Claptrap/Tirolio/Antiwalls can show one or two harmless energy drops; this prevents a single stray shot from disabling the farming mode. DroidPoet-like active wall shooters still switch to `dangerousWallEnemy()` after repeated fires.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `it_economics__ite_ctbot`):
- `/logs/rounds/0` shows `it_economics__ite_ctbot.MyTank`, a weak stop/go wall-heavy mover. It has avg speed ~2.6, is stopped ~31% of active ticks, near walls ~53%, short straight runs (median ~10 ticks), and only ~1.2 detected energy-drop shots/game. We swept all 250 games; `results.json` was `44413` vs `221`, avg round length ~435, our accuracy ~42%.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favors the damped wall/averaged predictors on these traces (`wallavg` mean future error ~44px, `avg` ~47px, head/linear/circular worse). The existing aggressive wall/straight farming modes are still appropriate because the opponent barely fires.
- Tiny targeted tweak in `robots/custom/MyTank.java`: the wall-bound straight-run `GUN_LINEAR` override now requires `straightEnemyScans > 12` instead of triggering on any single straight wall tick. This preserves prior Antiwalls/Claptrap long-edge-run behavior but avoids over-leading CTBot's short stop/reverse wall snippets before the virtual guns settle; short wall-bound CTBot starts with the damped averaged predictor instead.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `it_economics__ite_ctbot`, follow-up):
- Reviewed `/logs/rounds/1`: still swept all 250 games (`44607` vs `423`), with our accuracy ~42% and average round length ~440. CTBot remains a weak stop/go wall-heavy mover with low speed (~2.6) and only ~1-2 detected shots/game.
- Re-ran `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'`: damped `wallavg` predictor is still best overall (mean ~45px vs avg ~48, head ~55, lin/circ ~60). A category replay of actual shots showed CTBot's long `wall+straight`-looking snippets are still slow stop/reverse motion where `wallavg` (~49px) beats linear (~66px).
- Small code tweak in `robots/custom/MyTank.java`: the wall-bound straight-run `GUN_LINEAR` override now also requires a genuinely fast run (`abs(enemyVelocityAvg)>4.2` or current speed >5.5). This preserves Antiwalls/Claptrap long edge-slide behavior, but avoids over-leading CTBot's slow straight-looking wall snippets before virtual guns settle.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `it_economics__ite_simple`):
- `/logs/rounds/0` shows a weak Simple-style mover/shooter. We swept 250/250 games (`results.json` 45022 vs 174), avg round length ~471, our accuracy ~38%, opponent fires only ~2 shots/game and scores little damage. It moves max-speed in long mostly-straight runs and is near walls ~63% of sampled ticks.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favored normal averaged prediction overall (`avg` mean ~65px, linear/circular ~69px, old wall-damped `wallavg` ~81px). The old harmless-wall damped predictor under-led this opponent's faster wall runs.
- Code tweak in `robots/custom/MyTank.java`: harmless wall-bound targets still get damping for slow/stop-reverse snippets, but sustained fast/straight low-fire wall runners now use the normal averaged predictor (or the existing linear override when virtual scores allow it). This preserves CTBot-style damping while improving it_simple-style long wall runs. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `it_economics__ite_simple`, follow-up):
- `/logs/rounds/1` was still a 250/250 sweep and score improved slightly to `45057` vs `252` (near max; bullet damage/bonus up, ram leakage down). Average traced round length was ~494 turns, so there is still room to kill Simple faster but the matchup is very safe.
- Re-ran `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'`: normal averaged prediction remains best overall for this opponent (`avg` mean ~66.7px, linear/circular ~69px, wall-damped ~82.7px). A category replay showed even fast wall-straight samples favor averaged on mean error (~55.8px vs linear ~58.7), unlike earlier Claptrap/Antiwalls where linear won edge slides.
- Small retune in `robots/custom/MyTank.java`: the wall-bound fast-straight `GUN_LINEAR` override now lasts only for the first 18 virtual-gun samples and later requires linear to beat averaged by >4px, instead of forcing linear for 45 samples / within +8px. This should let it_simple switch to the better averaged predictor sooner while preserving a brief cold-start for true clean edge-slide bots.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `it_economics__ite_terminator`):
- `/logs/rounds/0` shows a valid Terminator bot. We swept all 250 games (`results.json` 44005 vs 572), avg traced round length ~435, our accuracy ~41%. Opponent is a weak stop/go wall-heavy mover/shooter: stopped ~52% of active ticks, near walls ~54%, avg speed ~2.47, and only ~2.7 detected shots/game. It sometimes makes short straight wall-looking bursts, but replay says damped wall/averaged prediction is best overall (`tools/offline_gun_eval.py`: wallavg mean ~55.8px, avg ~59.2, lin/circ ~61.5).
- Added `stopGoEnemyScans` in `robots/custom/MyTank.java`, a leaky counter for recent complete stops. This helps identify Terminator/CTBot-style stop/go wall motion even when `slowEnemyScans` resets during full-speed bursts.
- Tweaked wall-target handling: stop/go wall targets now keep the damped wall averaged predictor and a close ~285px orbit; fast-straight wall linear override is suppressed while recent hard stops are present and now requires a clearer virtual-gun linear margin. This should avoid over-leading Terminator's short wall snippets while preserving prior linear behavior for true clean edge sliders.
- `dangerousWallEnemy()` now ignores stop/go targets (`stopGoEnemyScans <= 12` required) so a few Terminator shots do not switch it into DroidPoet active-wall mode; DroidPoet-style continuous active wall shooters should still trigger after repeated fires.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `it_economics__ite_terminator`, follow-up):
- Reviewed `/logs/rounds/1`: still a full 250/250 game sweep (`results.json` 44016 vs 630), avg traced round length ~427 turns. Terminator remains a weak stop/go wall-heavy shooter with only a few detected shots per game; opponent score is small bullet leakage.
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` again favored the damped wall/averaged predictor overall (`wallavg` mean ~55.7px vs averaged ~58.8, linear/circular ~60.3). Category replay showed the damping also helps many *field* shots immediately after hard stops, not only true wall-bound shots.
- Small code tweak in `robots/custom/MyTank.java`: the `GUN_AVERAGED` predictor now uses the damped stop/go projection whenever a harmless low-fire target has recent hard stops (`stopGoEnemyScans > 8`), even if it has temporarily moved outside the 70px wall margin. Fast straight low-fire runs without recent stops still bypass this damping so Antiwalls/Claptrap/it_simple-style edge sliders can use fuller/linear prediction.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `tibola__markiv`):
- `/logs/rounds/0` opponent is `tibola__markiv.MyTank`, a MarkIV-like stop/go shooter. We swept all 250 games (`results.json` 43744 vs 1395), but it scores modest power-1 bullet damage (~56 per 10-round battle) and our rounds average ~506 ticks. Opponent stats from traces: stopped ~58% of ticks, near wall ~44%, avg speed ~2.0 but max 8 bursts, ~8 detected energy-drop shots/game.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` says damped averaged/wall-averaged prediction is best (`wallavg` mean ~49.7px, `avg` ~50.7, circular/linear/head-on ~64-66). Full linear/circular over-lead the repeated stops.
- Code tweak in `robots/custom/MyTank.java`: added `activeStopGoEnemy()` for repeated stop/go targets that are not Crazy/DroidPoet-style fast wall runners. When detected, use a closer ~305px orbit, keep max-power pressure while energy is safe, force `GUN_AVERAGED`, and allow the damped averaged predictor even after more than two detected enemy shots. This is aimed at MarkIV without disabling the existing dangerous-wall and hard-to-hit safeguards.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `tibola__markiv`, follow-up):
- Reviewed `/logs/rounds/1`: still a clean 250/250 sweep, but score slipped slightly vs round 0 (`43588` vs `1492`, avg traced length ~523 ticks, avg end energy still high at ~86). Opponent remains a MarkIV-style active stop/go shooter: many hard stops, short bursts, ~9 weak shots/game.
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` again favored damped averaged prediction (`wallavg` mean ~50.0px, `avg` ~51.7, linear/circular/head-on ~67). A quick fixed-power replay suggested faster/moderate bullets can improve geometric hit rate, but max power is still reasonable because we have large energy surplus and want bullet damage/bonus.
- Code tweak in `robots/custom/MyTank.java`: `activeStopGoEnemy()` now engages at `stopGoEnemyScans > 8` instead of `>14`, matching the damped predictor threshold so MarkIV field shots immediately after early hard stops/fires use the damped averaged gun. Active stop/go orbit tightened slightly from ~305px to ~285px to shorten bullet flight; survival margins are large in logs.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__regullarmonk`):
- `/logs/rounds/0` shows a real active stop/go shooter. We won aggregate (`36230` vs `7452`) and all 25 ten-round result files, but only survived 185/250 traced games; many losses were self-depletion after repeated power-3 misses while the opponent still had 20-50 energy. Opponent fires mostly power-1, is stopped about half the time, average speed ~2.8, low turn rate, and stays around 300-350px away.
- Offline shot replay over actual fire ticks favored head-on/low-power targeting for this opponent (head-on mean error ~65px at old powers; using faster ~1.2-1.6 bullets in replay brought head-on error down to ~52-55px, while linear/circular/averaged over-led). `tools/offline_gun_eval.py` also ranked head-on/wall-damped above full linear/circular.
- Added `activeStopGoShooter()` in `robots/custom/MyTank.java`: after repeated enemy fires plus stop/go low-turn motion, use a wider ~455px orbit, force `GUN_HEAD_ON`, and cap bullet power to ~1.45/1.15/0.55 depending on our energy instead of continuing the generic slow/stop-go max-power farming. This is intended to reduce rare self-depletion losses against RegullarMonk while preserving high-power modes for harmless stop/go bots and DroidPoet-style wall runners.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `robo_code__regullarmonk`, follow-up):
- Reviewed `/logs/rounds/1`: previous RegullarMonk-specific conservation improved survival slightly (195/250 vs 185/250) but reduced total score (`35728` vs `36230`) and made rounds much longer (~923 ticks avg vs ~771). Loss traces had us orbiting too wide (~448px avg in lost games), firing ~77 low-power bullets, then self-depleting while the opponent still had 20-40 energy.
- Kept the active stop/go shooter signature and head-on gun (replay still says head-on is best), but retuned the branch in `robots/custom/MyTank.java`:
  - active stop/go shooter orbit is now ~340px rather than 455px, matching the prior/high-scoring exchange range and shortening bullet flight;
  - healthy-energy bullet cap is slightly more assertive at close/normal range (1.65 under 430px, 1.35 farther), with lower caps only when our energy drops;
  - added a tighter firing tolerance for active stop/go shooter so even low-power conservation shots are not sprayed when the head-on gun is off.
- This is an informed retune without local battle execution; recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `andrekorol__oppswantmedead`):
- `/logs/rounds/0` shows `andrekorol__oppswantmedead.MyTank`, a weak fixed-heading stop/go shooter. We swept all 250 games (`results.json` 43141 vs 637), avg traced round length ~494, opponent fires ~8 weak power-1 shots/game, stops ~52% of ticks, body turn rate is essentially 0, and our end energy was very high (~102 avg) despite some low-power conservation behavior inherited from the RegullarMonk branch.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` says head-on is best by a clear margin (head mean ~54px, wallavg ~62, averaged ~72, linear/circular ~82). A quick power replay showed lower-power bullets have slightly smaller geometric error, but since the opponent is harmless and our energy surplus is large, max-power head-on pressure should improve kill speed/score.
- Added `fixedHeadingStopGoEnemy()` in `robots/custom/MyTank.java`: repeated stops + enemy fire + near-zero enemy turn-rate. This excludes the broader RegullarMonk `activeStopGoShooter()` conservation branch. For this signature, we use a closer ~305px orbit, force `GUN_HEAD_ON`, use high/max bullet power while energy is safe, and a moderate alignment tolerance. Other stop/go shooters with nonzero turn/noisier movement still use prior conservative/damped logic.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.


Round 2 (gpt-5-5 current edit against `andrekorol__oppswantmedead`, follow-up):
- Reviewed `/logs/rounds/1`: still a 250/250 sweep and score improved slightly (`43321` vs `565`), with avg round length ~444 ticks. Opponent is the same weak fixed-heading stop/go shooter: near-zero body turn rate, repeated stops/straight bursts, and mostly power-1 shots; our end energy is very high (~99 avg), so high-pressure fire is safe.
- Added a tiny `GUN_DRIFT_HEAD_ON` virtual gun in `robots/custom/MyTank.java`. For the fixed-heading stop/go signature it aims almost head-on but projects 5% of current enemy velocity along the fixed body heading (clamped to 0.45 px/tick). Offline shot replay over `/logs/rounds/1` showed this small drift is marginally better than pure head-on at current power-3 shots, while full linear still over-leads badly.
- `fixedHeadingStopGoEnemy()` now forces this drift-head-on gun; normal head-on/linear/circular/averaged behavior for other opponent classes is unchanged. The head-on-family virtual score now considers both pure head-on and drift-head-on for power/orbit heuristics.
- Added `tools/eval_fixed_heading.py` as a rough replay helper for this matchup. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__fire`):
- `/logs/rounds/0` shows sample.Fire-like opponent: mostly stationary, scans/gun turns, fires power-1 or power-3 depending on spawn range. We scored `44291` vs `668` and effectively swept 249/250 recorded games; the only non-win was a close-spawn tie where both bots got stuck colliding for ~90 ticks, trading ram damage and point-blank power-3 bullets until both died.
- Existing stationary-target farm mode was stopping movement after several stationary scans, which is good at normal range but bad when spawned inside/near ram distance of a stationary shooter.
- Updated `robots/custom/MyTank.java`:
  - stationary close-range targets (`stationaryScans > 5` and distance < 180) now force an immediate drive-away/center escape before the harmless stationary stop mode can engage;
  - `onHitRobot` now turns/drives directly away from the collision bearing (with a center fallback) instead of a simple `setBack`, reducing the chance of being pinned in close-spawn ram loops;
  - added helper `driveAlongAngle()` for these absolute-angle escapes.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `robo_code__fire`, follow-up):
- Reviewed `/logs/rounds/1`: score improved to `44421` vs `772` with 250/250 traced wins, but close-spawn traces (e.g. `sim_128`) still showed a point-blank stationary Fire fight where we drove inside ~50px, then repeated 0.6 ram/collision energy drops were misread as enemy fire. That caused direction flips every tick and kept us stopped in a damaging ram/bullet loop before eventually winning.
- Updated `robots/custom/MyTank.java` close stationary handling:
  - stationary close escape now keeps opening distance until ~260px (instead of only <180px) and drives a longer 240px escape vector;
  - `doMovement()` ignores the classic Robocode 0.6 ram/collision energy drop at <90px for enemy-fire reversal purposes, so close stationary spawn fights do not oscillate in place due to collision bookkeeping.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `philipmjohnson__dacruzer`):
- `/logs/rounds/0` shows a full 250/250 sweep (`results.json` 44031 vs 601), but rounds are long (avg ~788 ticks) and accuracy only ~19%.
- Opponent behavior: daCruzer is a mostly perimeter/wall cruiser (near wall ~93% of ticks), stopped ~44%, but its moving segments are long, fast, straight wall runs (median fast wall-straight run ~35 ticks). It fires only a handful of weak/varied shots (~6.5/game) and scores little damage.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favored full linear/circular prediction overall (`lin/circ` mean ~94px vs averaged ~124, wall-damped ~133, head-on ~161). A category replay showed fast wall-cruise samples especially favor linear (median future error ~0 vs damped under-leading badly).
- Code change in `robots/custom/MyTank.java`: added `fastWallCruiser()` detection for wall-bound, sustained fast straight runners with only modest firing. This branch uses a close ~305px orbit, high/max power while energy is safe, and forces `GUN_LINEAR` during those cruises. `dangerousWallEnemy()` now excludes this signature so daCruzer's few weak shots do not push us into the old DroidPoet averaged-gun branch. CTBot/Terminator-style slow stop/go wall bots should still use damped averaged prediction; DroidPoet-style heavy wall shooters still use dangerous-wall handling.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `philipmjohnson__dacruzer`, follow-up):
- Reviewed `/logs/rounds/1`: still a clean 250/250 sweep (`44004` vs `553`), but rounds remain long (~788 ticks avg) and our score/accuracy did not improve over round 0. daCruzer remains a perimeter cruiser (near wall ~92%, stopped ~45%, fast straight wall runs ~49%) and fires only a handful of mostly weak shots (~6/game; a few long rounds show >8 due to extended chasing).
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` again strongly favors full linear/circular prediction (`lin/circ` mean ~96px vs averaged ~125 and wall-damped ~134). My shot-time replay also shows linear is best on fast wall-straight segments, while our old dangerous-wall fallback can take over after the opponent's fire count passes 8 in long games and wrongly force averaged.
- Small code tweak in `robots/custom/MyTank.java`: relaxed `fastWallCruiser()` only after virtual guns have evidence that linear beats averaged by >12px, allowing daCruzer to stay in the close-orbit/full-linear branch up to 16 detected energy drops. Early behavior and DroidPoet-style active wall shooter safeguards remain unchanged.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `alpian__ianstank`):
- `/logs/rounds/0` is a real fixed-heading stop/go shooter. Aggregate is safely winning (`results.json` 40821 vs 1524; all 25 ten-round result files first place), but traced per-game survival has leakage: about 230/250 outright wins, 10 losses, 10 mutual-zero/draw-ish endings. Losses are mostly long games where we spend ourselves to zero while Ian's Tank keeps ~8-50 energy.
- Opponent signature from traces: body heading is constant (turn rate/span 0), it stops about 51% of ticks, moves in a small ~90px back/forth patch at max speed bursts, and fires regularly (~14 detected drops/game). `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` says head-on is best (mean ~68px) and lower-power/faster head-on bullets have much lower future-position error than old power-3 shots.
- Retuned only the `fixedHeadingStopGoEnemy()` branch in `robots/custom/MyTank.java`:
  - force pure `GUN_HEAD_ON` instead of the tiny drift-head-on gun (the drift was meant for max-power farming and can over-lead Ian's jitter);
  - cap bullet power to moderate/fast shots while healthy (about 1.75 under 430px, 1.45 farther), then 1.15/0.85 in mid energy, 0.45 below 18 energy, and 0.15 below 8 energy to avoid self-depletion;
  - tightened fixed-heading firing tolerance modestly (`atan2(18, distance)`).
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. If round-1 score drops too much, consider a middle ground (power ~2.0 early), but the main goal was eliminating the rare self-depletion losses.


Round 2 (gpt-5-5 current edit against `alpian__ianstank`, follow-up):
- Reviewed `/logs/rounds/1`: prior Ian-specific conservation improved from round 0 to a near sweep (`42130` vs `841`), with 249/250 traced wins and one mutual-zero draw instead of the earlier losses/draws. Opponent is still a fixed-heading stop/go shooter (body turn rate 0, ~51% stopped, ~100px back/forth segment, frequent power-1 shots).
- Offline shot replay showed that for this exact one-dimensional oscillator, aiming at the midpoint of its learned travel segment is much better than pure head-on or any velocity projection (rough mean future-position error ~39px vs head-on ~66px on `/logs/rounds/1`).
- Updated `robots/custom/MyTank.java`:
  - track the enemy position along a fixed heading axis (`enemyAxisMin/Max`) and, for confirmed fixed-heading stop/go enemies, reuse the drift-head-on virtual gun slot to aim at that segment midpoint;
  - slightly raised healthy-energy fixed-heading bullet caps (2.05/1.70) now that the midpoint gun should hit more often, while keeping low-energy pinprick safeguards to avoid self-depletion;
  - added a span guard so wide fixed-heading movers (>~210px axis span) do not get classified into the Ian-specific midpoint/conservation branch by accident.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `andrekorol__myfirstkiller`):
- `/logs/rounds/0` shows another fixed-heading stop/go/simple shooter. We swept 250/250 games (`results.json` 43071 vs 603), avg length ~510 turns, avg our end energy very high (~80), opponent fires mostly power-1 and never turns its body. It travels a wider line than the prior Ian/OppsWantMeDead tight oscillator, so the existing `fixedHeadingStopGoEnemy()` span guard often let it fall into the conservative `activeStopGoShooter()` branch.
- Offline replay: `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` ranks head-on best overall (mean ~52px, wall-damped ~59, averaged ~68, linear/circular ~77). `tools/eval_fixed_heading.py` showed a tiny velocity drift (~0.1 of current velocity) with moderate/fast bullets is slightly better than pure head-on; power 1.5-2.0 has lower geometric error than max power, but the opponent is weak and our energy surplus is large.
- Code update in `robots/custom/MyTank.java`: added `fixedHeadingLineEnemy()` for wide one-dimensional fixed-heading movers. This uses close ~305px orbit, `GUN_DRIFT_HEAD_ON` with a 10% velocity drift (not midpoint), and healthy-energy bullet power around 2.25-2.55 at normal ranges instead of the old active-stop/go 1.35-1.65 cap. Low-energy safeguards are preserved. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `andrekorol__myfirstkiller`, follow-up):
- Reviewed `/logs/rounds/1`: still a 250/250 sweep (`43116` vs `704`), average round length ~515 turns. MyFirstKiller is a fixed-heading one-dimensional stop/go shooter, but its line span averages ~180px (often >210px), unlike the tighter Ian/OppsWantMeDead oscillator that benefits from midpoint aiming.
- Offline replay with `tools/eval_fixed_heading.py '/logs/rounds/1/sim_*.jsonl'` showed small velocity drift remains best for this wider mover (`~45.85px` mean error vs `~46.16px` head-on), while midpoint aiming is bad on wide-line games (`~59.6px` overall, especially poor for spans >210px). Midpoint was only better for very tight spans (<~125px).
- Code tweak in `robots/custom/MyTank.java`: narrowed `fixedHeadingStopGoEnemy()` / midpoint classification from axis span <210px to <135px, and require `fixedHeadingLineEnemy()` to have span >=135px. This keeps the Ian-style tight oscillator midpoint gun, but routes current MyFirstKiller-style wider line movers to the 10% drift-head-on gun and heavier fixed-line pressure earlier/more consistently.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `alpian__tarektank`):
- `/logs/rounds/0` shows `alpian__tarektank.MyTank`, a fixed-heading one-dimensional oscillator: it runs back/forth over about a 100px line segment, stops roughly half the time, and fires many weak power-1 bullets. We swept all 250 games (`results.json` 42596 vs 719), but average rounds were ~551 ticks and the longest self-depleted down to 6.5 energy.
- Offline replay over traces showed the existing learned-axis midpoint gun is much better for this opponent than head-on/linear/averaged (`axis` mean future error ~40px vs head-on ~68px). The main inefficiency was the broad `fixedHeadingStopGoEnemy()` conservation profile capping shots to ~1.7-2.05 power.
- Added enemy fire-power averaging plus `weakFixedAxisOscillator()` detection (fixed-heading, learned axis span ~42-125px, weak average fire power <=1.35). For this Tarektank-like profile the bot now orbits closer (~245px), uses heavy 3.0/2.65 bullets while healthy, and relaxes gun tolerance back above the previous fixed-heading tight cap. Broader fixed-heading stop/go shooters still keep the older conservative power rules.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, Tarektank follow-up):
- Reviewed `/logs/rounds/1`: previous weak fixed-axis oscillator tuning improved score to `42945` vs `876` with a 250/250 sweep; Tarektank still runs a compact fixed-heading back/forth line (~100px learned axis span) and fires weak power-1 bullets. Our energy remains safe but rounds average ~529 ticks.
- Additional offline shot replay over actual fire ticks showed the learned-axis midpoint gun was geometrically stable but not the best for this oscillator: aiming near the *opposite endpoint* of the learned line segment gave far lower mean error (~35.5px vs ~40px midpoint) and an approximate hit fraction over 50% vs ~14% with a strict 18px threshold, because the target often reverses to the other end during bullet flight.
- Updated `robots/custom/MyTank.java`: for `weakFixedAxisOscillator()` only, the drift-head-on gun slot now aims at the opposite learned endpoint, inset by ~8-16px for noise/robot width. The existing midpoint behavior remains for broader `fixedHeadingStopGoEnemy()` cases, and wide fixed-heading line movers still use small velocity drift.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `it_economics__ite_cliffbot2`):
- `/logs/rounds/0` shows a safe 250/250 sweep (`results.json` 44624 vs 809), avg traced round length ~341 ticks, our end energy very high (~124). Opponent is a weak stop/go mover/shooter: stopped ~48% of ticks, average speed ~2.5, few shots/game (mostly power 1/2), and not strongly wall-bound (~5%).
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` clearly favored head-on prediction (`head` mean ~42px) over damped averaged/wallavg (~46-55px) and linear/circular (~66px). The existing generic active stop/go branches could late-game route this kind of target into averaged or RegullarMonk-style low-power conservation, despite the high energy surplus.
- Added `easyHeadOnStopGoEnemy()` in `robots/custom/MyTank.java`: after virtual guns confirm low head-on error and a clear margin over averaged/circular, weak stop/go targets keep close ~285px orbit, high-power firing, and forced `GUN_HEAD_ON`. This is gated by virtual error/fire-count so MarkIV/Terminator damped-stopgo and RegullarMonk conservation behavior should remain intact.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `it_economics__ite_cliffbot2`, follow-up):
- Reviewed `/logs/rounds/1`: still a clean 250/250 game sweep. Score improved over round 0 to `44864` vs `1132`, average traced round length ~338 ticks, survival was perfect, and our end energy averaged ~122. Cliffbot2 remains a weak stop/go mover/shooter (stopped ~38%, avg speed ~3, only ~2.5 detected shots/game) and is not strongly wall-bound.
- Re-ran `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'`: head-on prediction remains clearly best (`head` mean ~42px vs damped wallavg ~46, averaged ~54, linear/circular ~65). Existing `easyHeadOnStopGoEnemy()` high-power/head-on branch appears to be working.
- Tiny code tweak in `robots/custom/MyTank.java`: `easyHeadOnStopGoEnemy()` can engage after 10 virtual samples instead of 14, and its preferred orbit is tightened from 285px to 255px. This is meant to shorten max-power bullet flight against this very safe target while keeping the branch gated by low head-on virtual error/fire count so the older RegullarMonk/MarkIV conservation branches are not affected.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.
