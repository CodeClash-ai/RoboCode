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

Round 1 (gpt-5-5 current edit against `team488__meow`):
- `/logs/rounds/0` shows `team488__meow.MyTank`, a Crazy-like high-speed turner: avg speed ~5.5, body turn median ~0.086 rad/tick, ~50% of ticks match the existing `crazyEnemyScans` signature, fires ~6.6 shots/game with only ~7% accuracy. We swept 250/250 games (`results.json` 42200 vs 325), avg round length ~417, our accuracy ~54%, survival 100%, end energy high (~91).
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favors the existing circular gun (mean future error ~59px vs averaged ~63, head-on/wallavg/linear much worse). A quick shot-time replay showed lower-power circular shots are geometrically more accurate, but because this opponent is safe and our hit rate is high, heavier bullets likely improve damage/kill speed while energy is abundant.
- Small code tweak in `robots/custom/MyTank.java`: when the Crazy signature is active *and* virtual waves confirm a low circular error (`virtualGunError[GUN_CIRCULAR] < 85` after >14 samples), tighten orbit from 305 to 275 and allow heavy/max bullets (3.0 under ~620px while energy >45, guarded downshift below that). If a future Crazy-style opponent remains hard to hit, the old moderate-power 305px behavior is preserved until virtual evidence says it is easy.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `team488__meow`, follow-up):
- Reviewed `/logs/rounds/1`: still a perfect 250/250 sweep and score improved to `42542` vs `358` (avg traced round length ~395, down from ~416). Meow remains a safe Crazy-like high-speed continuous turner; opponent mainly fires weak power-1 shots and our survival/end-energy margin is large.
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` still strongly favors circular prediction (`circ` mean ~68px at actual mostly-power-3 shots, far ahead of head-on/linear; moderate hypothetical powers reduce geometric error but max power appears to maximize damage/score while energy is abundant).
- Tiny follow-up in `robots/custom/MyTank.java`: Crazy signature engages one scan earlier (`crazyEnemyScans > 3`), easy-circular close orbit tightened from 275 to 260, and high-energy max-power circular mode extends from range 620 to 680. This is an aggressive but narrow tweak for the current predictable/safe high-speed turner; older low-energy caps remain in place.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__corners`):
- `/logs/rounds/0` is sample.Corners-like. We swept all 250 games (`results.json` 44607 vs 1092), avg round length ~382 and end energy ~116, but a few close-spawn games leaked a lot of energy. Worst trace (`sim_160`) had the enemy drive through us, then both bots got pinned at ~36px with repeated 0.6 collision drops and point-blank bullets before we won with only ~28 energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` says normal averaged/linear/circular prediction are all good (avg ~41.8px, linear/circ ~42px); no gun retune seemed necessary.
- Updated close-range movement in `robots/custom/MyTank.java`:
  - added a broader low-fire/wall/stop-go close escape below ~185px before normal orbit logic, not just for already-stationary targets below 260/118px;
  - added `driveAwayFrom()` helper that chooses a separation angle with wall/corner safety, and reused it for stationary close escape and `onHitRobot` (verified after final compile);
  - intent is to avoid Corners/Fire-style spawn ram loops where wall smoothing or center fallback curves us back across the opponent.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `robo_code__corners`, follow-up):
- Reviewed `/logs/rounds/1`: still a 250/250 sweep (`44543` vs `978`), but close-spawn/drive-through traces (e.g. `sim_240`, `sim_225`) still showed Corners pinning us around 40-50px for many ticks. We eventually win, but leak energy to point-blank fire/collisions.
- Tightened the close-escape behavior in `robots/custom/MyTank.java`:
  - low-fire/wall/stop-go targets now trigger direct `driveAwayFrom()` until distance is >225px (was 185px), so we keep opening a gap instead of resuming orbit while Corners is still driving through us;
  - `driveAwayFrom()` now accepts direct escape vectors closer to walls (24px safety instead of `WALL_MARGIN+8`) and scores candidate angles more heavily by separation, because the previous conservative wall-margin check could bend a valid escape into a centerward curve across the enemy.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `it_economics__ite_florian2`):
- `/logs/rounds/0` shows `it_economics__ite_florian2.MyTank`, a stop-heavy but mobile shooter. We swept 250/250 traced games and won aggregate (`42297` vs `2016`), but it fires mostly power-3 shots (~4-5/game), average opponent speed is low (~1.5, stopped ~72%), and our score/round length (~426 turns) leave room for faster farming.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favored the damped wall/stop-go averaged predictor (`wallavg` mean ~44.8px, head-on ~46.9, normal avg ~51.5, linear/circular ~57.7). The existing `activeStopGoShooter()` branch was designed for RegullarMonk-style weak/evasive shooters and could switch this target to low-power head-on after several detected fires.
- Added `heavyStopGoShooter()` in `robots/custom/MyTank.java`: detects repeated high-power enemy fire plus stop/go/low-turn movement. This keeps a close ~275px orbit, forces `GUN_AVERAGED`, and uses high pressure (power 3/2.45 while energy is healthy) instead of the conservative active-stop/go cap. It is excluded from `dangerousWallEnemy()` and hard-to-hit tightening so the current Florian2 profile stays in the damped high-pressure branch.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.
- Follow-up detail: also routed `heavyStopGoShooter()` through the stronger damped averaged predictor inside `predictEnemy()` (same damping family as harmless stop/go/wall targets), not just the gun-selection override. This matches the offline `wallavg` result for Florian2.

Round 2 (gpt-5-5 current edit against `it_economics__ite_florian2`, follow-up):
- `/logs/rounds/1` stayed a clean 250/250 game sweep (`42405` vs `2274`) against Florian2. Opponent is very stop-heavy (~72% stopped, avg speed ~1.55) but fires mostly power-3; our previous Florian2 branch raised our score slightly but also allowed more opponent bullet damage.
- Re-ran `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` and a category replay. Overall wall-damped averaged was best, but at our shot times when the enemy was currently stopped/slow, head-on/linear/circular were clearly better than the damped averaged gun because averaged still carries EMA drift and over-leads the stop.
- Tiny targeted gun tweak in `robots/custom/MyTank.java`: inside `heavyStopGoShooter()`, use `GUN_LINEAR` when current enemy speed is <=3.25 (identical to head-on when stopped, better for slow rolls), and keep `GUN_AVERAGED` for fast bursts where wall-damped prediction is best. Power/movement were left unchanged to avoid overfitting.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__myfirstrobot`):
- `/logs/rounds/0` shows Robocode sample.MyFirstRobot-like fixed-heading stop/go shooter. We swept all 250 games (`results.json` 42788 vs 752), but score is lower than recent easy bots: rounds average ~500-700 ticks and our end energy can drop to ~60 because the target pauses at endpoints and our Tarektank-specific opposite-endpoint gun can over-lead.
- Trace stats: opponent body heading is almost fixed, stopped ~48%, moves in ~100px spans, fires mostly power-1 (~9-10 shots/game). `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` ranks head-on best overall (mean ~67px), with damped wall/avg second and full linear/circular poor. A shot-time replay also showed head-on beating the weak-axis opposite-endpoint aim overall for this endpoint-pausing pattern.
- Tiny code tweak in `robots/custom/MyTank.java`: for `fixedHeadingStopGoEnemy()` / `weakFixedAxisOscillator()` targets, the drift-head-on slot still handles learned-axis midpoint/opposite-endpoint aim for Ian/Tarektank-style oscillators, but a new `weakAxisHeadOnIsBetter()` virtual-wave guard allows pure `GUN_HEAD_ON` when head-on error beats the drift/axis gun by >5px after >16 samples. This is meant to adapt to MyFirstRobot endpoint pauses without removing the previous Tarektank optimization.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `robo_code__myfirstrobot`, follow-up):
- Reviewed `/logs/rounds/1`: still a 250/250 sweep, but score slipped vs round 0 (`42530` vs prior `42788`) and average game length was ~556 turns. Opponent remains fixed-heading, one-dimensional, compact oscillation (median learned span ~119px), weak power-1 firing, ~47% stopped.
- Replayed shot timing with the same learned-axis state used in `MyTank`: despite the previous note, the axis opposite-endpoint gun is much better than pure head-on for this trace family at actual/max powers (rough power-3 future error ~45.7px and ~40% <24px vs head-on ~65px and ~20% <24px). The older analysis that favored head-on was not using the learned axis predictor accurately.
- Code changes in `robots/custom/MyTank.java`:
  - removed the `weakAxisHeadOnIsBetter()` override from gun selection, so compact fixed-axis oscillators force the drift/axis gun again;
  - tuned the opposite-endpoint aim inset from 15% to 10% of learned span (clamped 8-16px), which was marginally best in replay;
  - tightened the weak fixed-axis orbit from 245px to 225px to reduce bullet flight against this safe weak shooter.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `kylebennett__gruffalo`):
- `/logs/rounds/0` shows a real stop/go mover/shooter, `kylebennett__gruffalo.MyTank`. We swept all 250 traced games and won aggregate (`41611` vs `1612`), but average game length was ~492 ticks and Gruffalo scored small but repeated medium-power bullet damage.
- Trace stats: opponent is stopped ~53% of active ticks, wall-bound ~38%, low-turn/straight-ish ~80%, fires around 7.5 detectable shots/game with average power ~2.0. Our end energy is usually high (~91 avg) but rare long rounds can drop to ~10.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favors the damped wall/stop-go averaged predictor (`wallavg` mean ~55px, normal avg ~61, head-on ~68, linear/circular ~74), so the old RegullarMonk-style active stop/go branch forcing low-power head-on is not ideal here.
- Added `mediumStopGoShooter()` in `robots/custom/MyTank.java`: detects stop-heavy, low-turn enemies with repeated medium-power fire. It keeps a ~300px orbit, forces `GUN_AVERAGED` (with the damped stop/go predictor), and uses high pressure while our energy is safe (power 3 under ~560, 2.35 farther) with low-energy downshift. It is excluded from `activeStopGoShooter()` and `dangerousWallEnemy()` so Gruffalo does not fall into the conservative head-on/DroidPoet branches.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `kylebennett__gruffalo`, follow-up):
- Reviewed `/logs/rounds/1`: still a clean 250/250 game sweep and score improved from `41611` to `41815`. Gruffalo remains a stop-heavy, low-turn mover/shooter with medium (~power-2) fire; our damped averaged gun is best, but the previous wall/stop-go damping was a little too conservative/under-leading on its continued bursts.
- Ran `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'`: wall-damped averaged prediction still wins overall (`wallavg` mean ~56px vs normal avg ~62, head ~68). A small coefficient replay over sampled traces suggested Gruffalo does better with more current/EMA velocity than CTBot/Terminator-style damping.
- Changed `robots/custom/MyTank.java` only inside the `mediumStopGoShooter()` damped averaged predictor: use `0.45*currentVelocity + 0.65*enemyVelocityAvg`, capped at 2.2 (1.4 when currently stopped), instead of the generic `0.25/0.35` cap-2.2 damping. Other wall/stop-go opponents keep the old damping.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__ramfire`):
- `/logs/rounds/0` shows sample.RamFire-like opponent. We swept all 250 games and scored near maximum (`results.json` 45168 vs 433), avg game length ~327 ticks, 73% hit rate. Opponent rarely lands bullets; its only leakage is close-spawn rams/point-blank shots (avg min distance ~101, ~10% ticks under 150px).
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favored linear/circular prediction on these traces (`lin/circ` mean future error ~68px vs averaged ~79px and head-on ~98px). RamFire often drives straight toward/through us, so the prior harmless straight-run branch could force damped averaged prediction and under/over-lead the current charge line.
- Added `closeRammerScans` / `lowFireRammer()` in `robots/custom/MyTank.java`: detects close, low-fire, low-turn chargers; uses a ~260px preferred distance and forces `GUN_LINEAR` before the generic harmless straight-run averaged override. Existing close-escape logic still opens distance under ~225px, and max-power harmless-runner firing remains unchanged.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `robo_code__ramfire`, follow-up):
- Reviewed `/logs/rounds/1`: still swept all 250 games and score stayed near max (`45106` vs `323`), with average game length down to ~308 turns. Remaining leakage is almost entirely close-range RamFire contact/point-blank bullet damage; traces still show ~12 ticks/game under 100px and 60/250 games with min distance under 50px.
- Kept the successful linear-gun/max-power RamFire handling, but made the rammer movement more assertive in `robots/custom/MyTank.java`: once `lowFireRammer()` is confirmed, direct `driveAwayFrom()` now stays active until ~300px instead of falling back to the generic 225px close-escape/orbit band, and `lowFireRammer()` engages a little earlier/tolerates up to 4 detected energy drops so a few collision/point-blank events do not disable the branch mid-round.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `rafaeljdesa__ultron`):
- `/logs/rounds/0` is a real active opponent, `rafaeljdesa__ultron.MyTank`. We won aggregate (`38902` vs `2794`) and first place in all 25 ten-round result files, but traced game wins were only 246/250 with 2 losses and 2 draws. Failure cases were long rounds where our previous slow/stop-go max-power branches spent us to zero while Ultron still had energy.
- Trace stats: opponent stops/reverses often (about 31% stopped, median turn rate 0, stop/go signature common), but average speed is higher than Florian/Gruffalo (~3.7 abs speed) and it fires many mostly power-3 bullets (~8.7 detected drops/game). Offline replay over actual shot times strongly favored head-on prediction (`head` mean ~64px overall; in stop/go categories head-on beat linear/circular/averaged/wallavg). Hypothetical lower bullet powers improved head-on geometric error and should reduce self-depletion.
- Code changes in `robots/custom/MyTank.java`:
  - added `enemySpeedAvg` and a new `highPowerStopGoDodger()` signature for Ultron-like high-power, moderate-speed stop/go dodgers;
  - this signature uses a moderate ~355px orbit, forces `GUN_HEAD_ON`, bypasses the generic slow/stop-go max-power branch, tightens fire tolerance, and caps bullet power to fast/cheap shots (roughly 1.15-1.45 when healthy, 0.65-0.85 mid energy, tiny pinpricks when low) instead of max-power slow-target pressure;
  - narrowed `heavyStopGoShooter()` with `enemySpeedAvg < 2.55` so the prior Florian2 high-pressure damped branch should not catch Ultron-style faster dodgers.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `rafaeljdesa__ultron`, follow-up):
- Reviewed `/logs/rounds/1`: prior Ultron-specific head-on/low-power branch slightly improved total score (`38964` vs `38902`) and traced survival (247/250 wins, 1 loss, 2 draw-ish endings), but rare bad rounds still self-depleted after falling back into heavier shots at low energy while Ultron retained 5-15 energy.
- Kept the head-on `highPowerStopGoDodger()` idea, but made it stickier and added `activeHighPowerShooter()` as a late-round safety net for repeated high-power enemy shooters:
  - high-power/low-turn moderate-speed shooters stay in the Ultron branch even if the leaky stop/go counter decays during long reversals;
  - below 18 energy, widen preferred distance to at least ~430px against active high-power shooters;
  - below 30/18/10 energy, cap shots to 0.85/0.45/0.15 when facing repeated high-power fire, preventing the remaining self-depletion traces from spending multi-point bullets while near death.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `kylebennett__hugbot`):
- `/logs/rounds/0` shows another very weak/harmless mover, `kylebennett__hugbot.MyTank`. We swept 250/250 games with near-perfect score (`results.json` 45023 vs 201). Opponent usually does not fire (mean detected shots ~0.2/game), moves at max speed in long straight field-crossing runs, and only gets tiny ram/collision leakage.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favors full linear/circular prediction on Hugbot traces (`lin/circ` mean ~77px vs damped averaged ~92px, head-on ~124px). The existing harmless non-wall straight-run branch forced the damped averaged gun due to prior Tirolio/Claptrap stop/reverse traces.
- Small targeted tweaks in `robots/custom/MyTank.java`: harmless non-wall straight runners still cold-start with `GUN_AVERAGED`, but after virtual waves show linear at least 5px better (or a very clean high-speed/low-stop signature appears) the branch now promotes to `GUN_LINEAR`; also added a mild early `driveAwayFrom` for close harmless straight runners (<260px) to reduce Hugbot's rare ram/collision leakage. This should improve Hugbot/Antiwalls-style long straight field runs without affecting stop/reverse harmless movers where averaged remains better.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `kylebennett__hugbot`, follow-up):
- `/logs/rounds/1` stayed a 250/250 sweep and improved total score (`45110` vs `323`), avg game length ~316 and accuracy ~79%. Hugbot remains effectively harmless but max-speed straight field crossings still cause occasional point-blank contact/stray bullet leakage: 24/250 games had live distance <50px, opponent score mostly bullet/ram damage.
- Offline gun eval continues to strongly favor linear/circular on Hugbot (`lin/circ` mean future error ~76.7 vs averaged ~92.5), so kept the previous linear promotion for clean harmless straight runners.
- Small movement-only retune in `robots/custom/MyTank.java`: harmless straight runners now trigger direct `driveAwayFrom()` much earlier (`straightEnemyScans > 2`, distance <310) and stretch to ~305px, and sustained non-wall straight-run orbit widened from 310 to 335. Also let `harmlessLowFireEnemy()` tolerate up to 3 detected drops so one point-blank stray/collision does not disable Hugbot farming. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `andrekorol__exterminador`):
- `/logs/rounds/0` shows a strong aggregate win (`45560` vs `3951`, first in all 25 ten-round battle files) but trace survival had 6/250 losses. Losses were self-depletion/trading around ~125-150px after the opponent stopped and fired repeated power-3 shots; our end score is high but close-range leakage is avoidable.
- Opponent stats: stop/go with ~51% stopped ticks, long low-turn straight runs when moving (~49% straight), little wall time (~3%), average detected fire power near 3.0. `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favors full linear/circular prediction (`lin/circ` mean ~60.8px) over averaged (~67.9) and head-on (~89), so the old damped stop/go or high-power-shooter head-on fallbacks are not ideal.
- Added `straightStopGoLinearEnemy()` in `robots/custom/MyTank.java`: after virtual waves confirm linear clearly beats averaged/head-on on a low-turn straight+stop target, force `GUN_LINEAR`, use a moderate ~315px orbit, keep high power while energy is abundant, and downshift below ~38 energy to avoid rare self-depletion. It also directly opens range below ~245px. The branch is virtual-error gated and excluded from Ultron/RegullarMonk high-power/head-on safety paths only when linear evidence is present.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `andrekorol__exterminador`, follow-up):
- Reviewed `/logs/rounds/1`: aggregate still very strong (`45447` vs `3439`, first in every 10-round battle), but trace survival leaked 6/250 losses. The losses had Exterminador stop/park on a nearly fixed heading around 135-160px, fire repeated power-3 shots, and our bot's fixed-heading/active high-power safety reduced our shots to ~1.1-2.0 power while we ate 16-damage hits; several rounds ended with the opponent alive on ~12-18 energy.
- Added `fixedHeadingHighPowerShooter()` in `robots/custom/MyTank.java`: repeated high-power fire + stop/go + near-zero turn rate. This excludes the weak power-1 fixed-axis oscillator branches.
- For this signature: use a short/moderate ~300px preferred orbit, force head-on while stopped / linear while moving, keep decisive high-power shots while energy is healthy (power 3 under ~540px), use max-power finishers when the opponent is under ~17 energy, and only downshift otherwise when energy is genuinely low. Also added a perpendicular close escape under ~260px so if it parks near a wall we sidestep the firing line instead of driving directly into the wall/corner and trading point-blank.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__tracker`):
- `/logs/rounds/0` shows Robocode sample.Tracker-like behavior. We swept all 250 traced games and won aggregate (`45852` vs `2400`), but Tracker leaked nontrivial bullet damage: about 216 detected enemy shots over 250 games, mostly power-3, with many close approaches (about 27% of live ticks under 225px, ~11% under 150px). Average round length was short (~312 ticks) and `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favored linear/circular prediction (`lin/circ` mean ~63px vs averaged ~72, head-on ~95).
- Added a narrow `lowFireTracker()` signature in `robots/custom/MyTank.java`: detects sustained low-turn radial approaches toward us (Tracker driving down the bearing line) with only modest fire count and no wall/Crazy signature.
- For this signature the bot now opens direct separation until ~390px, uses a wider ~355px preferred orbit, forces the linear gun, and keeps max/high-power shots while energy is safe. Intent: preserve the proven linear farming while reducing point-blank Tracker power-3 leakage; RamFire/Hugbot-style low-fire chargers should still be safe under this wider separation.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. Local `robocode.sh` still cannot load custom/sample robots in this stripped workspace, so no local battle result was available.

Round 2 (gpt-5-5 current edit against `robo_code__tracker`, follow-up):
- Reviewed `/logs/rounds/1`: still a clean 250/250 game sweep and aggregate improved to `46083` vs `3040`, but Tracker's leakage increased versus round 0. Per-result aggregation showed all opponent score is bullet damage (3040 total) while our survival/bonus is max; trace stats still had ~101/250 games dipping under 100px and ~54/250 under 50px, with worst rounds eating 6-8 detected mostly power-3 Tracker shots.
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` still strongly favors linear/circular prediction (`lin/circ` mean ~66.8px vs avg ~75.9/head-on ~100) at actual shots, so the Tracker gun remains forced linear and max/high-power.
- Small movement retune in `robots/custom/MyTank.java`: made the Tracker-specific radial-approach signature tolerate up to 12 detected shots (so it does not fall out after a few close power-3 leaks), widened direct separation from ~390/380 to ~415/405, and raised its preferred orbit from 355 to 380. Intent is to reduce point-blank Tracker bullet damage while preserving the proven linear farming gun.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `luke_f_w__nagisphere`):
- `/logs/rounds/0` shows a stationary but active shooter: NagiSphere never moves (`xspan/yspan=0`) but repeatedly fires mostly power-1.5 bullets. We swept 250/250 games (`results.json` 43509 vs 1204), but some traces showed it landing a long series of medium bullets at ~250-270px while we farmed it.
- Added `stationaryShooter()` in `robots/custom/MyTank.java`: once a stationary target has actually fired, use a wider ~455px preferred orbit instead of the previous close stationary/head-on farming band. Stationary non-firing targets still stop and farm as before. Gun/power remain max-power head-on for stationary targets.
- Intent: exact max-power shots still kill reliably, while the wider moving orbit gives a stationary gun longer bullet flight and less repeated hit leakage. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, NagiSphere follow-up):
- Reviewed `/logs/rounds/1`: still a 250/250 sweep vs stationary active shooter `luke_f_w__nagisphere`, but the prior 455px stationary-shooter orbit reduced our total score (`43107` vs round-0 `43509`) despite slightly reducing opponent score. The loss was mostly lower bullet damage/bonus; exact max-power stationary shots kill faster from the old closer band.
- Retuned `robots/custom/MyTank.java` stationary-shooter movement: active stationary targets now prefer ~330px again (faster farming), while the close-spawn stationary escape is stronger. If spawned within 260px and the direct away vector would run into a wall/corner, we now use a perpendicular escape; otherwise we open to 300px before resuming orbit. This targets the bad close-corner traces without sacrificing normal-range kill speed.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__velocirobot`):
- `/logs/rounds/0` shows `robo_code__velocirobot.MyTank`, a medium-fast low-turn/straight-run mover that fires frequent weak ~power-1 bullets. We won 249/250 with one draw (`results.json` 41642 vs 3042), but long games self-deplete (avg length ~588, max 1087, avg end energy ~65 but some wins at <1 energy).
- Trace stats: avg enemy speed ~4.4, stopped only ~4%, straight-motion ticks ~67%, wall-bound ~21%, detected enemy fire ~19/game at avg power ~1.05. Existing Crazy/straight branches could over-lead with circular/linear and spend mostly power-3.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favored head-on/wall-damped predictors over full linear/circular overall. A quick shot-time replay suggested lower/faster bullets materially improve geometric hit chance versus the old power-3 shots.
- Added `velociRobotEnemy()` in `robots/custom/MyTank.java`: detects weak-firing, medium-fast, low-stop straight runners. This branch uses a ~345px orbit, forces damped `GUN_AVERAGED`, caps healthy shots to ~1.85-2.3 (lower when energy falls), and avoids the Crazy circular/max-power override. Also added `enemyAbsTurnRateAvg` for this signature. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `robo_code__velocirobot`, follow-up):
- Reviewed `/logs/rounds/1`: the first VelociRobot specialization still won the aggregate (`40734` vs `3629`) but regressed from round 0 (`41642`), with 6 traced losses and one mutual-death draw. Losses were long weak-fire exchanges where our lower power cap prolonged the round until we self-depleted; average length rose to ~619 ticks.
- Offline shot-time replay on VelociRobot traces shows faster bullets reduce geometric error a lot, and head-on/wall-damped aim is competitive/better than full lead; however round-1's 1.85-2.3 healthy cap was too conservative for scoring/finishing.
- Retuned only the VelociRobot branch in `robots/custom/MyTank.java`:
  - healthy shots now use more pressure (roughly 2.1-2.75 instead of max 2.3), mid-energy shots use ~1.35-1.9, and tiny bullets are reserved for genuinely low energy;
  - when energy falls below 22, preferred distance widens to at least 440 while using cheap fast shots to reduce late weak-bullet deaths;
  - VelociRobot gun can switch from damped averaged to head-on when virtual errors are comparable (head-on +3px), since replay shows head-on often wins for this medium-speed weak shooter at faster powers.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `avsthiago__sadbot`):
- `/logs/rounds/0` shows a full 250/250 game sweep (`results.json` 42435 vs 2112). SadBot is a stop/go low-turn mover/shooter: ~67% stopped, ~52% wall-bound, avg speed ~2.0, and fires ~7.5 medium-power shots/game (avg drop ~1.8). Our survival/end energy is excellent (avg min energy ~82), so the opportunity is faster kills / less bullet leakage rather than safety.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favors wall-damped/averaged prediction overall (`wallavg` mean ~47.8px, avg ~48.4, lin/circ ~53.3, head ~61.2). A quick shot-time category replay showed currently-stopped SadBot ticks are better with head-on/linear than carrying averaged EMA drift, while moving ticks still prefer damped averaged.
- Code tweak in `robots/custom/MyTank.java`: for `mediumStopGoShooter()` (the branch SadBot matches), tighten preferred range from 300 to 285, and choose `GUN_HEAD_ON` only when the enemy is currently stopped and head-on virtual error is not far behind averaged; otherwise keep the damped averaged gun. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `avsthiago__sadbot`, follow-up):
- Reviewed `/logs/rounds/1`: still a 250/250 game sweep and essentially unchanged score (`42444` vs round-0 `42435`). SadBot remains a medium-power stop/go shooter (about 64% stopped, ~39% wall-bound in a quick trace scan, avg detected fire power ~1.85). Our end energy is usually very high (~95 avg), so faster kills are the main opportunity.
- Added `tools/analyze_sadbot.py`, a small trace summarizer for this matchup (live ticks, distances, stop/wall fractions, detected energy drops/end energy).
- Retuned the `mediumStopGoShooter()` path in `robots/custom/MyTank.java` for earlier/more aggressive SadBot handling:
  - trigger after fewer medium-power shots/stop-go scans (`fireCount > 1`, `stopGo > 6`);
  - tighten preferred range from 285 to 260 while the generic close escape still prevents true point-blank trades;
  - keep max/high-power shots down to lower energy (`>24` instead of `>34`, max power to 600px);
  - choose head-on for currently stopped SadBot ticks more readily (early before virtual waves settle, or unless head-on is far behind averaged). Moving ticks still use the damped averaged gun.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `looklazy__chilibot`):
- `/logs/rounds/0` shows a strong aggregate win (`results.json` 42338 vs 5469; first in all 25 ten-round battles), but traced per-game survival leaked 2 losses and 2 mutual-zero/draw-ish endings. Losses were long rounds where Chilibot kept a fixed body heading, stopped ~64% of ticks, moved in straight forward/back bursts, fired ~10 medium/high bullets/game (avg detected power ~2.06), and our weak fixed-line/medium-stopgo branches eventually spent down to zero while it survived on ~13-15 energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favored head-on / damped wallavg prediction for this opponent (`head` mean ~33.8px, wallavg ~37.4, averaged ~45, linear/circular ~52.9); the old tiny drift/linear line guns over-led its stops.
- Added `fixedHeadingMediumShooter()` in `robots/custom/MyTank.java`: repeated medium-power fire + near-zero turn-rate stop/go motion. It forces pure `GUN_HEAD_ON`, uses a moderate 360px orbit (widening to 430 when low energy), and applies high pressure while healthy (max/2.35) with low-energy pinprick safeguards. It is excluded from generic medium/active stop-go branches and guarded away from weak fixed-axis oscillators and Exterminador-style high-power cases.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.
- Final tiny addition after compile: `fixedHeadingMediumShooter()` also has a max-power finisher when the enemy is under ~17 energy and we have >6, and the generic low-energy caps skip that finisher. This directly targets the two Chilibot loss traces where low-power conservation left it alive on ~13-15 energy.


Round 2 (gpt-5-5 current edit against `looklazy__chilibot`, follow-up):
- Reviewed `/logs/rounds/1`: aggregate improved to `42607` vs `5221`, but traces still had 2 Chilibot wins and 1 mutual-death draw. Remaining failures were late long rounds: Chilibot is fixed-heading/stop-go, fires medium/high bullets, and hit us after we had burned down to low energy; several cases had it under ~14-29 energy while our old movement stayed near 330-450px and our finishers spent expensive power-3 shots.
- Kept the successful pure head-on `fixedHeadingMediumShooter()` gun, but added two safety/finishing tweaks in `robots/custom/MyTank.java`:
  - fixed-heading medium shooters now widen earlier as our energy falls (455px below 32 energy, 515px below 18) and perform a short perpendicular escape immediately after detecting an enemy fire drop when our energy is low, instead of only reversing on the same line;
  - when a fixed-heading medium/high shooter is nearly dead and our energy is below 34, cap firepower to the minimum lethal bullet. This sends faster bullets and preserves the 1-2 energy margin that decided the remaining Chilibot loss/draw traces.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__spinbot`):
- `/logs/rounds/0` shows Robocode sample.SpinBot-like behavior: velocity ~5 with continuous body turn (~0.06 rad/tick avg in traces), compact circular motion, and a few weak/random shots. We swept 250/250 games (`results.json` 42679 vs 1218), avg round length ~353, avg final energy ~121.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favors circular prediction (`circ` mean future error ~31px at actual shots; a custom replay with fixed power showed circular is near-exact, ~8-11px mean vs head-on ~76-82 and linear much worse). Existing `crazyEnemyScans` missed SpinBot because it requires velocity >5.2, so early shots used head-on/averaged and many power-2 bullets.
- Added `spinEnemyScans` / `spinBotEnemy()` in `robots/custom/MyTank.java`: detects sustained speed ~4.15-5.35 plus continuous turn. Once active it uses a ~340px orbit, forces `GUN_CIRCULAR`, and upgrades to max/high-power bullets while energy is safe. This should shorten SpinBot rounds and reduce random bullet leakage without affecting high-speed Crazy/Meow, straight VelociRobot, or fixed-heading stop/go branches; it also prevents the generic Crazy power cap from overriding SpinBot max-power farming if both counters briefly trigger.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.


Round 2 (gpt-5-5 current edit against `robo_code__spinbot`, follow-up):
- `/logs/rounds/1` improved the SpinBot specialization from round 0 (`42922` vs `42679`, opponent score down `664` vs `1218`), with a clean 250/250 traced sweep. Remaining leakage is only occasional random/sample SpinBot power-3 bullet hits; our end energy remains very high (quick trace scan mean min energy ~91), so faster kills are worth a bit of controlled range tightening.
- Offline replay on round-1 traces still strongly favors circular/averaged prediction (`circ` mean ~36.9px, avg ~37.3px; head/linear much worse), and actual shots were almost all max-power. Kept max-power SpinBot handling.
- Small targeted tweak in `robots/custom/MyTank.java`: once SpinBot is confirmed and virtual waves show low circular error, preferred distance tightens from 340 to ~305 to reduce bullet flight / round length. Also let the SpinBot gun switch to damped averaged only if virtual errors show a clear averaged margin; otherwise it remains forced circular. Recompiled with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.
- Local `robocode.sh` still cannot find custom/sample robots in this stripped workspace (empty battle result), so rely on harness logs for evaluation.

Round 1 (gpt-5-5 current edit against `iagomonteiro13579__npcsniper`):
- `/logs/rounds/0` opponent is `iagomonteiro13579__npcsniper.MyTank`. Current bot wins aggregate (`38700` vs `3268`) but traced survival is only 243/250 wins with 6 losses and 1 mutual-death draw.
- Quick trace scan: opponent is a medium-fast low-turn straight/wall runner (avg abs speed ~4.4, ~24% stopped, ~31% wall-bound, ~53% straight ticks) firing many medium bullets (~4121 detected drops over 250 games, avg power ~1.58). Losses are long rounds where our max/near-max shots self-deplete while the opponent remains on ~25-50 energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favors damped wall/averaged prediction over head/linear/circular (wallavg ~90px, avg ~95, linear/head/circ >102). A custom quick replay found a slightly less damped wall predictor (~0.30 current velocity + 0.45 EMA, capped 2.6) a few px better for this trace set.
- Code changes in `robots/custom/MyTank.java`: added `npcSniperEnemy()` signature for active medium-fire fast straight/wall runners (triggers after 4 detected shots); it forces the damped averaged gun with the custom damping, widens orbit to ~405/485 when low, tightens fire tolerance, excludes the generic dangerous-wall max-power mode, and caps bullet power to faster medium/cheap shots (healthy max ~2.2, mid ~1.45, low-energy pinpricks). Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `iagomonteiro13579__npcsniper`, follow-up):
- Reviewed `/logs/rounds/1`: previous NPCSniper branch improved aggregate score (`39203` vs round-0 `38700`) and survival to 249/250, but one traced loss (`sim_202`) still self-depleted. The late loss pattern was our bot falling out of the NPCSniper-specific caps while low on energy, then spending repeated ~0.5-0.6 shots and getting clipped by medium bullets while the enemy had ~14 energy.
- Added a sticky `npcSniperScans` confirmation counter so once the medium-fire fast/straight wall-runner profile appears, the averaged damped gun / power caps stay active through brief late-round stops or wall departures.
- Retuned NPCSniper low-energy survival: below 28/18 energy the preferred distance widens to ~500/540, enemy-fire drops trigger a short perpendicular dodge when our energy is low, and below 24 energy bullet caps are slightly cheaper (0.50/0.30/0.20 tiers) to avoid self-depletion while preserving healthy-energy pressure.
- Added `tools/analyze_npcsniper.py` to summarize long NPCSniper traces (distances/min energy/winners). Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `gabriel_lw__quadwall`):
- `/logs/rounds/0` shows a strong aggregate win (`44334` vs `2035`) but not a perfect survival sweep: trace winners were 247/250 for us, 3 losses. QuadWall is a wall/perimeter runner: ~82% wall-bound, ~42% stopped, ~39% fast ticks, frequent weak shots (avg detected drop ~0.87, ~8/game). Losses were long wall chases where we self-depleted while QuadWall survived with ~9-29 energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favored the normal averaged predictor (`avg` mean ~98.5px) over circular/linear (~101-104) and wall-damped (`wallavg` ~103). The existing fastWallCruiser/dangerousWall paths could force linear or high max-power too long.
- Added `quadWallEnemy()` in `robots/custom/MyTank.java`: active weak-fire wall+stop profile with a virtual-error guard that averaged is not losing to linear. This branch forces normal `GUN_AVERAGED`, excludes fastWallCruiser/dangerousWall, uses a moderate ~325px range (widening to 430 when low), keeps max pressure while healthy, then downshifts earlier to cheap/tiny bullets below 28 energy to avoid the observed self-depletion losses.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `gabriel_lw__quadwall`, follow-up):
- Reviewed `/logs/rounds/1`: the first QuadWall-specific branch regressed aggregate score (`42926` vs round-0 `44334`) and traced survival (237/250 wins plus one mutual-zero ending). The branch's early medium/low bullet caps prolonged rounds; in several traces QuadWall survived with high energy while our bot died by energy hitting zero, even though its gun is weak.
- Kept the useful `quadWallEnemy()` detection and forced normal `GUN_AVERAGED` (offline replay on round 1 still favors averaged: ~98.5px mean vs circular/linear ~100-104), but partially rolled back the conservative movement/power:
  - preferred range is back near the aggressive wall-farming band (~305px while healthy, only widening to 430 below 18 energy);
  - QuadWall now uses max/high pressure until energy <24 (power 3 under ~650, 2.55 farther) and only switches to cheap/tiny bullets when genuinely low. This should recover kill speed/score while preserving a low-energy self-depletion guard.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.
- Tiny finisher addition: when confirmed QuadWall is below ~10.5 energy, cap to `lethalPower(enemyEnergy)` so the final shot is faster and avoids overkill/self-depletion energy waste.

Round 1 (gpt-5-5 current edit against `zcjerry229__markrobo`):
- `/logs/rounds/0` shows a winning but not perfect matchup: aggregate `40528` vs `4439`, traced survival 241/250 wins, 8 losses, 1 mutual/draw. MarkRobo is a low-turn stop/go mover with repeated medium-power firing (avg speed ~1.85, stopped ~56%, median turn 0, ~11 detected shots/game avg power ~1.86). Losses were long self-depletion duels where we kept close/high-power exchanges and died with the opponent still on ~4-75 energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favored damped wall/averaged prediction (`wallavg` mean ~42px, normal avg ~44, linear/head/circ ~56-58). A quick fixed-power replay showed lower-power/faster bullets improve geometry substantially for this stop/go target, but max power is still useful while energy is high.
- Added `mediumStopGoDuelist()` in `robots/custom/MyTank.java`: after repeated medium shots on a low-turn stop/go target, force the damped averaged gun, keep close/max pressure only while healthy/early, then widen and cap firepower (cheap/tiny bullets below ~34/16 energy) to avoid self-depletion. It also sidesteps on enemy-fire ticks when our energy is low. The broader Gruffalo/SadBot `mediumStopGoShooter()` branch remains for easier medium stop/go opponents.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `zcjerry229__markrobo`, follow-up):
- Reviewed `/logs/rounds/1`: aggregate remained winning (`40517` vs `4558`) but the first MarkRobo duelist branch did not improve round-0 score and traces still showed 8/250 losses. Losses are self-depletion/medium-bullet duels: MarkRobo is low-turn stop/go, fires repeated medium shots, and our bot could keep using too many high-power bullets before fully widening/conserving.
- Re-ran `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'`; damped wall/stop-go averaged remains best (`wallavg` mean ~41.7px vs avg ~44.2, head/linear/circular ~56-58), so kept `mediumStopGoDuelist()` forced to `GUN_AVERAGED` with damped velocity.
- Retuned `robots/custom/MyTank.java` more defensively for this profile:
  - `mediumStopGoDuelist()` now engages earlier (`enemyFireCount > 3`, `stopGo > 6`);
  - preferred distance opens sooner (about 390px once energy <58 or after many enemy fires, 460px below 28 energy) instead of staying at 285px until energy <50;
  - bullet power caps are more conservative after the opening: max power only above 70 energy and <=8 detected enemy shots, then ~1.15-1.65, then cheap 0.55-0.75 / 0.15-0.30 tiers at lower energy;
  - enemy-fire sidestep for MarkRobo now starts below 44 energy instead of 34.
- This may trade a little bullet damage in easy games for fewer self-depletion losses, which were the main remaining weakness. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `dankraemer__juggernaut`):
- `/logs/rounds/0` is a much more dangerous power-3 stop/turn opponent, `dankraemer__juggernaut.MyTank`. We still won aggregate (`39857` vs `5253`) and all 25 ten-round battle result files, but individual traces were 243 wins / 6 losses / 1 draw. Losses were long self-depletion/power-3 exchange rounds: Juggernaut averaged ~15.7 detected p3 shots in losses, and our old generic high-power stop/go logic often forced head-on plus very cheap bullets, leaving it alive to fire more.
- Offline replay over current traces favored the normal damped averaged predictor for Juggernaut (`avg` mean error ~80 overall / ~89 on loss-shot samples; head-on/linear/circular were worse). Power buckets suggested p3 bullets were not ideal against this evasive turn/stop pattern, while 1.5-2.3 power averaged shots remained reasonably accurate.
- Added `juggernautEnemy()` in `robots/custom/MyTank.java`: repeated p3 fire, medium-high average speed, stop/go, and nontrivial turn rate. When detected, it:
  - forces `GUN_AVERAGED` instead of the generic high-power head-on branch;
  - uses a wider orbit (~385px, 480px when low energy) and sidesteps perpendicular on every detected Juggernaut shot;
  - uses medium bullet power while healthy (about 1.95-2.35) with stricter low-energy caps, and bypasses the generic `activeHighPowerShooter()` cap that was reducing shots too far.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `dankraemer__juggernaut`, follow-up):
- Reviewed `/logs/rounds/1`: aggregate still won (`39676` vs `5000`) but traced survival stayed at 244/250. Losses were all self-depletion against the same power-3 stop/turn bot; in late rounds Juggernaut sometimes parked/stopped long enough for our `juggernautScans` sticky counter to decay, after which generic close/slow branches fired 2+ power bullets even below ~18 energy (e.g. `sim_13`), while the enemy survived with 20-80 energy.
- Kept the averaged-gun Juggernaut targeting (offline replay still favors averaged: avg ~80px vs linear ~88/head ~96), but made the Juggernaut profile truly sticky once detected, so late parked phases cannot fall back into generic high-power/slow-target logic.
- Tightened low-energy Juggernaut survival: below 32 energy widen to ~500px (540 below 18), use the global active-high-power caps for Juggernaut too, and drop to cheap/tiny bullets below 32/18 instead of mid-power shots. Also added a perpendicular escape when low energy and inside ~320px.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__trackfire`):
- `/logs/rounds/0` shows a stationary active shooter, `robo_code__trackfire.MyTank`. Aggregate is a safe win (`43255` vs `7355`), but traced survival leaked 5 opponent wins and 6 unresolved/draw-ish endings. Opponent never moves, but after locking it fires repeated power-3 bullets; loss traces show our old stationary-shooter orbit reversing on every energy drop and stalling around ~235px, causing nearly every p3 bullet to land while we self-depleted/traded.
- Added `stationaryHeavyShooter()` in `robots/custom/MyTank.java` (stationary target with average detected fire power >2.2). For this TrackFire-style signature, enemy fire drops no longer call the normal orbit `reverseDirection()`; instead we immediately take a perpendicular escape so we do not sit on the same head-on line.
- Heavy stationary shooters now prefer a wider ~455px orbit (505px when our energy is low) while still using exact max-power head-on shots. Also added a low-energy minimum-lethal finisher against heavy stationary shooters to avoid wasting overkill energy in close endgames.
- Stationary weak/medium shooters (e.g. prior NagiSphere/Fire farming notes) keep the old closer ~330px stationary shooter band. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, TrackFire follow-up):
- Reviewed `/logs/rounds/1`: aggregate score improved (`45680` vs `12180`) but traced survival regressed to 243/250 wins plus one mutual draw. Losses/draws show stationary `robo_code__trackfire` landing repeated power-3 bullets while our bot oscillates at a nearly fixed ~285px point; the pure perpendicular stationary-heavy dodge from the prior edit did not actually open range.
- Updated `robots/custom/MyTank.java` stationary-heavy movement only:
  - added `driveStationaryHeavyEscape()`, a diagonal away+perpendicular escape that keeps a consistent dodge side, scores both separation and lateral motion, and avoids wall/corner traps;
  - stationary power-3 shooters now use this diagonal escape on detected fire and whenever inside ~430px, opening toward the existing wide ~455px orbit instead of circling in place in TrackFire's firing line;
  - `onHitByBullet` no longer reverses/overwrites movement for confirmed stationary heavy shooters; it continues the same diagonal escape using the last scanned enemy bearing.
- Gun/power logic is unchanged (exact max-power head-on with low-energy lethal finisher). Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `joaomcarvalho__jeujdapeu`):
- `/logs/rounds/0` shows a winning but lossy matchup: aggregate `36811` vs `4879`, trace survival 245/250 wins and 5 losses.
- Opponent is a medium-speed turning mover (avg speed ~3.7, abs turn ~0.08 rad/tick, not strongly wall-bound) that fires many mostly power-3 bullets (~12/game; losses ~20/game). Offline `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favored averaged/head-on (`avg` mean ~60.7, head ~63.3; linear/circular worse). Losses were long high-power exchanges where we self-depleted while it had ~5-49 energy.
- Added `turningHighPowerEnemy()` in `robots/custom/MyTank.java`: repeated p3 fire + medium-speed non-wall turning, excluding Juggernaut/fixed-heading/Crazy/etc. This branch forces `GUN_AVERAGED`, uses a moderate/widening orbit (400 healthy, 500/550 low), sidesteps on enemy fire, caps bullets to medium power while healthy and tiny bullets when low, and uses a small lethal finisher for very low enemy energy. Aim tolerance is tightened for this branch.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, Jeujdapeu follow-up):
- Reviewed `/logs/rounds/1`: aggregate improved to `37236` vs `3958`, with 249/250 traced wins and one mutual-zero/draw (`sim_5`). Previous turning-high-power branch worked overall but the remaining failure was a late low-energy p3 exchange: our bot had ~5-6 energy, enemy was under ~3, and our low-energy pinpricks plus very wide orbit let an old enemy power-3 bullet catch us before the final hit.
- Kept `turningHighPowerEnemy()` forced to `GUN_AVERAGED` (offline replay still favors averaged: mean ~59.5px vs head ~64.8, circular ~84.7, linear ~104).
- Small retune in `robots/custom/MyTank.java`:
  - widened Jeujdapeu low-energy orbit sooner (`~505` below 40 energy, `~555/585` in the final reserve) and made `onHitByBullet` for this signature continue a larger perpendicular escape instead of the generic 170px reversal;
  - lowered routine low-energy bullet caps a little more below 22/12 energy to reduce self-depletion in long p3 exchanges;
  - added a final-finisher override: if confirmed Jeujdapeu is below ~3.6 energy and we have enough reserve, fire `lethalPower(enemyEnergy)` instead of endless tiny pinpricks, to avoid another mutual-zero endgame.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `it_economics__ite_m9`):
- `/logs/rounds/0` aggregate was a win (`29368` vs `11899`), but trace survival was poor: quick parser (`tools/analyze_m9.py`) estimated only ~159/250 wins, 91 losses. Losses were classic self-depletion in long wall/stop-go exchanges: M9 is a wall-bound stop/go mover (avg speed ~1.4, ~58% stopped, ~65% wall, low turn) firing repeated ~power-2 bullets; when we drifted to ~420px average distance we spent high/max power until zero while M9 often survived with 20-90 energy.
- Offline replay (`tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'`) strongly favors damped averaged/wallavg aim (`wallavg` ~32px, avg ~36px, head/linear ~48-50px). A small param sweep suggested a tighter velocity cap with more current+EMA carry may improve this target.
- Added `m9WallStopGoEnemy()` in `robots/custom/MyTank.java`: power-2, low-speed, low-turn, wall+stop/go signature. It forces `GUN_AVERAGED`, uses custom damping `limit(-1.5, velocity + enemyVelocityAvg, 1.5)`, prefers a closer ~315px band while healthy (430 only low energy), tightens fire tolerance, and caps bullet power to medium/faster shots (about 2.0-2.3 healthy, 1.1-1.6 mid, pinpricks low) plus small lethal finishers. This aims to trade a bit of raw bullet score for fewer self-depletion losses.
- Added `tools/analyze_m9.py` for future trace summaries. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `it_economics__ite_m9`, follow-up):
- Reviewed `/logs/rounds/1`: aggregate improved to `31927` vs `10437`, but traced per-game survival is still lossy (our bot alive 180/250, M9 alive 70/250). M9 is a wall/corner-heavy stop/go bot, ~61% stopped, ~72% near walls, repeatedly firing ~power-2 bullets. Losses are long rounds where we drift ~440-500px, many shots hit walls, and M9 survives while our energy self-depletes / gets clipped by p2 bullets.
- Offline replay of actual M9 shot opportunities confirms the custom M9 damped wall predictor in `predictEnemy()` is excellent (mean future error ~20.6px vs generic wallavg ~31.6), so gun choice stayed `GUN_AVERAGED`/M9 damping.
- Tweaked `robots/custom/MyTank.java` specifically for M9:
  - M9 signature now engages earlier (wall/stop-go >6, >1 detected p2 shot, avg speed 0.65-2.65) instead of waiting for several shots; this should leave generic max-power wall farming sooner.
  - On every detected M9 fire tick, drive a perpendicular escape, not only below 38 energy, because loss traces showed p2 hits landing during the healthy-energy phase too.
  - Healthy M9 orbit tightened from ~315 to ~285 (reserve 390 instead of 430) to shorten bullet flight and reduce wall misses; still widens when low.
  - M9 bullet powers reduced to faster medium bullets (roughly 1.55-1.9 high energy, 0.95-1.35 mid, tiny when low) with a capped lethal finisher, aiming to prevent self-depletion while preserving the accurate damped gun.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `admiralrasmussen__wavesurfing`):
- `/logs/rounds/0` is a real wave-surfing/evasive opponent. Aggregate is winning (`results.json` 24226 vs 10440), but traced survival is poor: quick scan showed only ~72/250 games with our bot alive, ~174 with the opponent alive, plus a few mutual-zero endings. Opponent appears to score almost no bullet damage in Robocode results; it wins rounds mostly because our bot spends itself to zero while landing enough bullet damage to win aggregate.
- Opponent signature: medium/fast evasive mover (avg speed ~4.5, avg abs turn ~0.074 rad/tick, ~23% stopped, not strongly wall-bound) with very high virtual-gun errors. `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` says head-on/wall-damped are least bad (`head` mean ~107, `wallavg` ~109) and full linear/circular are worse.
- Added a narrow `waveSurfingEnemy()` branch in `robots/custom/MyTank.java`: high virtual error + medium/fast turning + low apparent enemy fire (sticky, tolerates hit-induced drops), excluding Crazy/SpinBot/rammers/wall cruisers. It forces head-on unless averaged is clearly better, tightens aim tolerance, uses close ~295px range while healthy (430 when low), and caps power aggressively (roughly 1.1-1.35 opening, then 0.65/0.3/tiny) to avoid self-depletion. Also disabled the generic `headOnGunIsBest()`/slow-target max-power boosts and hard-to-hit 355px override for this signature.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. No local battle execution is available in this stripped workspace, so this is based on trace analysis only.

Round 2 (gpt-5-5 current edit, WaveSurfing follow-up):
- Reviewed `/logs/rounds/1`: first wave-surfing branch regressed aggregate (`19202` vs round-0 `24226`) and traced survival (about 41/250 wins, 205 losses). Opponent still scores essentially zero bullet damage; losses are self-depletion after many low-power bullets, often leaving the surfer with 20-40 energy.
- Offline shot replay on round-1 traces showed faster low-power bullets reduce prediction error, but the old caps were too weak for kill pressure and still let us dribble energy to zero. The guess-factor virtual gun has worse mean error but a much higher near-hit fraction on this surfer.
- Updated `robots/custom/MyTank.java` for `waveSurfingEnemy()` only:
  - engage the signature earlier (`virtualSamples > 10`, lower error threshold) to avoid generic power-3 opening shots;
  - use more assertive healthy-energy bullets (~1.6-1.95) then sharply conserve;
  - below 14 energy, stop firing unless the enemy is nearly dead, preventing the observed self-disable losses;
  - allow the GF virtual gun after enough samples when it is not catastrophically worse than head-on, aiming for more actual hits despite higher mean positional error.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `tannerrogalsky__tannerbot1`):
- `/logs/rounds/0` aggregate is already winning (`31857` vs `7097`), but traced survival is lossy: quick parser showed about 185/250 live wins, 57 live losses, 8 mutual/draw-ish endings. Opponent is almost always wall-bound (~95% near wall), with long straight cardinal runs (~65% straight ticks), many stops (~33%), low turn rate (~0.018 avg abs heading delta), and repeated medium fire (~18.5 detected shots/game, avg power ~1.92). Losses are mostly self-depletion in long wall/corner chases, often with the enemy parked on a wall/corner and still holding 10-70 energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favors full linear/circular lead for this trace set (`lin/circ` mean ~125 px) over normal averaged (~133), wall-damped (~151), and head-on (~172). So unlike M9/QuadWall, this wall runner should stay on linear aim, but should not inherit generic max-power wall farming.
- Added `mediumPowerWallCruiser()` in `robots/custom/MyTank.java`: wall+straight, medium-power (~p2), medium/fast (EMA threshold lowered to tolerate corner stops), low-turn signature for TannerBot. It forces `GUN_LINEAR`, uses a moderate 315px band (440 when low), sidesteps on detected fire and opens range when low/close, tightens aim tolerance, and caps bullet power to medium/faster shots (about 1.6-1.95 while healthy, 1.05-1.45 mid, tiny when low) with a small lethal finisher. This is intended to reduce self-depletion while preserving the replay-best linear gun.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `tannerrogalsky__tannerbot1`, follow-up):
- Reviewed `/logs/rounds/1`: prior Tanner branch improved aggregate (`32383` vs `31857`) and traced survival (199/250 wins vs 185/250), but still lost 46/250 with 5 draws. Losses are self-depletion wall chases: Tanner remains ~97% wall-bound, ~59% straight, fires repeated ~p1.8-2.0 bullets; our bot often spent 45-70 shots and died while Tanner retained 20-70 energy.
- Added `tools/analyze_tanner.py` for quick Tanner trace summaries. Offline replay of round-1 shot opportunities still favors linear aim, but a stopped-tick variant with tiny EMA drift improved replay error (pure linear mean ~76px, stopped-EMA linear ~74px, averaged/wallavg much worse).
- Updated `robots/custom/MyTank.java` Tanner handling:
  - added sticky `tannerWallScans` via `tannerWallCruiserRaw()` so corner stops do not drop back into generic wall/slow max-power behavior;
  - Tanner profile engages earlier (after >1 medium shots and lower wall/straight counters) and stays active late;
  - slightly tightened healthy orbit (300px) but opens to 405 only below 22 energy;
  - reduced medium/low bullet caps a bit (healthy ~1.55-1.9, mid ~0.98-1.35, low ~0.30-0.50) to reduce self-depletion while preserving linear kill pressure;
  - for `GUN_LINEAR` on confirmed Tanner, stopped ticks now carry a tiny EMA velocity drift instead of pure zero-velocity head-on;
  - `onHitByBullet` for Tanner now uses a larger perpendicular escape rather than the generic 170px reversal.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `vikdov__dominatorx`):
- `/logs/rounds/0` is a winning but lossy matchup: aggregate `33583` vs `11283`, but traces show only about 218/250 clean wins, ~31 losses and one mutual-zero ending. DominatorX is an active medium-power shooter (detected drops avg ~1.9, ~25/game) with medium/fast movement, many low-turn straight legs, wall/corner bounces/stops, and occasional turns. Losses are mostly long self-depletion duels where our previous wall-cruiser/NPC/Tanner branches over-led or conserved too late while it survived with 10-40 energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` says head-on is best overall for these traces (`head` mean ~75px, `wallavg` ~81, averaged ~95, full linear/circular >120). A quick category replay also favored head-on/wall-damped over full lead even on straight-looking segments, because the bot often stops/reverses/wall-bounces during bullet flight.
- Added a sticky `dominatorEnemy()` signature in `robots/custom/MyTank.java`: repeated p~2 fire, speed avg ~3-6.4, some straight motion, nontrivial but not crazy turn rate. It is checked before NPCSniper/Tanner/wave-surfer-style branches.
- For DominatorX the bot now forces `GUN_HEAD_ON`, uses a moderate/widening orbit (~365 healthy, 440/500 when energy drops), sidesteps on detected fire, tightens gun tolerance, and caps bullet power to faster medium shots while healthy (~1.6-2.05) with cheap/tiny low-energy tiers plus small lethal finishers. Also excluded DominatorX from the dangerous-wall max-power safety net.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, DominatorX follow-up):
- Reviewed `/logs/rounds/1`: aggregate improved from round 0 (`34068` vs `10579`, traced ~219/250 wins vs ~216/250), but remaining losses were still self-depletion. Several loss traces showed the DominatorX branch not engaging/sticking early enough: our bot fired power-3 shots down to ~20 energy in some games, then dribbled 0.1-0.3 bullets to zero while DominatorX retained 40-100+ energy.
- Kept DominatorX forced to `GUN_HEAD_ON` (offline replay on round-1 traces still favors head-on across win/loss buckets; linear/circular badly over-lead, wallavg is close but worse than head-on).
- Tightened `robots/custom/MyTank.java` Dominator handling:
  - `dominatorSignatureRaw()` now engages earlier and with a stickier/broader mixed straight/wall/stop-go p~2 signature (lower fire/sample/speed/turn thresholds), to avoid falling into generic max-power wall/slow branches during early or late parked phases.
  - Dominator power caps are more conservative: only ~1.55-1.9 above 68 energy, ~0.9-1.25 mid, sub-0.42 below 24, and tiny reserve shots thereafter.
  - Added a final reserve guard: below 9 energy, stop firing if Dominator still has >12 energy, instead of self-disabling with harmless pinpricks.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `alexbay218__shreker`):
- `/logs/rounds/0` shows a winning but lossy matchup: aggregate `31744` vs `14148`; trace.md reports 228/250 wins, 13 Shreker wins, 9 draws. Shreker is a low-turn stop/go/straight mover (avg speed ~2.8, stopped ~26%, straight ~62%) that fires many mostly power-3 bullets (~25-32 detected drops/game, avg drop ~2.8). Losses/draws are self-depletion or p3 streams in long rounds, often with Shreker still alive on 5-15 energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` says head-on is best overall (`head` mean ~47px) with damped wallavg second (~52px); full linear/circular over-lead (~76px). A quick fixed-power replay showed lower/faster head-on bullets reduce future error vs p3.
- Added sticky `shrekerEnemy()` detection in `robots/custom/MyTank.java`: repeated high-power fire + low-turn stop/go/straight motion. This branch forces head-on unless virtual waves clearly favor damped averaged, uses medium/cheap bullet caps (healthy ~1.55-1.95, low-energy pinpricks/lethal finishers), moderate/widening orbit (~345 healthy, 455/535 low), perpendicular dodges on Shreker fire and bullet hits, a reserve no-fire guard below 10 energy when Shreker is not near death, and a custom damped averaged predictor when selected.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, Shreker follow-up):
- Reviewed `/logs/rounds/1`: Shreker follow-up regressed slightly (`31305` vs `15194`; traced ~217 wins, 29 losses, 4 draws vs previous ~228/13/9). Losses were mostly late p3/endgame issues: the raw Shreker signature could fade during fast wall runs or after hit-induced energy changes, letting generic branches fire power-3 at low energy; the old low-energy movement widened to ~500px, making our final head-on/pinprick bullets too slow while old Shreker p3 bullets were already in flight.
- Added `tools/analyze_shreker.py` for quick win/loss energy/shot summaries from Shreker sim logs.
- Tweaked `robots/custom/MyTank.java` Shreker branch:
  - once detected, `shrekerScans` now stays sticky for the rest of the round (like Juggernaut) instead of decaying to zero;
  - low-energy Shreker orbit is more compact (about 330/360/420 instead of 345/455/535) and the direct low-energy escape only forces range from true knife range (<240), avoiding very long final shots;
  - if Shreker is under ~18 energy and we have >10 energy, use capped lethal/near-lethal shots to finish instead of endless tiny pinpricks;
  - if we are below 22 while Shreker is still above 18, stop firing rather than self-disabling with tiny bullets that cannot kill soon.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `pez__wallspoet`):
- `/logs/rounds/0` opponent is a hard active wall/stop-go bot, `pez__wallspoet.MyTank`. We won aggregate (`29350` vs `10101`) and all 25 ten-round battle files were first place, but survival was only about 202/250 traced games (44 losses, 4 draws). Opponent is wall-bound most of the time, stopped ~50% of active ticks, max-speed bursts otherwise, near-zero turn-rate EMA, and fires frequent power-3 bullets (~17/game). Our old dangerous-wall branch used close ~335px orbit and power-3/2.35 shots; offline replay showed p3 has much larger future-position error on this stop/go target than fast low/medium bullets.
- Added `wallsPoetScans` / `wallsPoetEnemy()` signature in `robots/custom/MyTank.java` for high-power wall-bound stop/go opponents. It engages after repeated p3 fire + wall/stop-go/low-turn signature (median ~120 ticks in old traces), before generic `dangerousWallEnemy()`.
- For this signature: use normal `GUN_AVERAGED`, a wider adaptive orbit (~390 healthy, 450 mid, 500 low energy), and cap power to faster/cheaper shots (roughly 1.35-1.85 healthy, 0.75-1.2 mid, pinpricks low, lethal capped shot only when enemy is nearly dead). This should reduce self-depletion and improve survival against Wallspoet while preserving prior DroidPoet/weak-wall branches.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. Local Robocode battle still only tests sample bots, not the hidden opponent.

Round 2 (gpt-5-5 current edit, Wallspoet follow-up):
- Reviewed `/logs/rounds/1`: the first Wallspoet branch regressed (`27949` vs round-0 `29350`) and survival fell to 184/250 wins with 55 Wallspoet wins and 11 draws. Trace stats show Wallspoet is persistently wall-bound (~87-92%), stop/go, near-zero turn, and fires repeated power-3 shots. Loss/draw games still contained many of our p3/near-p3 energy drops, suggesting the new Wallspoet caps were not reliably owning the matchup.
- Root cause found in `robots/custom/MyTank.java`: the existing sticky `shrekerEnemy()` and fixed-heading weak-line classifiers could also match this wall-bound p3 stop/go profile, and they were checked before `wallsPoetEnemy()` in movement/gun/power. That routed Wallspoet into compact head-on/Shreker or fixed-line behavior instead of the intended averaged wall gun and medium-power conservation.
- Code changes:
  - `wallsPoetEnemy()` is now checked before Shreker in gun choice, and `shrekerEnemy()` explicitly yields when Wallspoet is confirmed;
  - `wallsPoetScans` is sticky for the rest of the round once detected, and raw detection engages after >1 p3 enemy fire drop;
  - fixed-heading stop/go/line weak-bot classifiers now ignore high-power (`enemyFirePowerAvg > 2.35`) opponents so they do not steal Wallspoet;
  - added a Wallspoet low-energy no-fire reserve guard below 12 energy unless the enemy is near lethal range;
  - kept the replay-best normal `GUN_AVERAGED` predictor and existing Wallspoet medium/low power caps.
- Added `tools/analyze_wallspoet.py` for quick win/loss summaries. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.


Round 1 (gpt-5-5 current edit against `josephjeon__gntest`):
- `/logs/rounds/0` shows a real, two-mode opponent. We won aggregate (`39111` vs `8250`) and all 25 ten-round result files, but traced survival had 11/250 losses. Overall GNTest often moves fast with continuous turning (offline eval strongly favors circular: `circ` mean ~76px), but the loss traces are different: it slows/stops on a nearly fixed heading, fires repeated medium bullets (avg drop ~2.2), and our bot self-depletes from long 400px+ exchanges while GNTest keeps 15-65 energy. Loss-only replay favored head-on/circular over linear/averaged.
- Added a narrow name-gated `gntestStopDuel()` branch in `robots/custom/MyTank.java` for the slow/low-turn medium-fire GNTest phase. It does not affect other opponents because it requires `enemyName.contains("josephjeon__gntest")`.
- In that branch: use compact-but-safe range (~325, widening only when low), force head-on when stopped/creeping and circular if it resumes turning, cap bullets to faster medium/cheap shots with a lethal finisher, tighten aim tolerance, stop last-reserve firing when the enemy is not near death, and sidestep on detected GNTest fire once our reserve is below 50.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, GNTest follow-up):
- Reviewed `/logs/rounds/1`: aggregate improved slightly (`39257` vs `8093`) but traced survival still had 11 GNTest wins. Loss traces (e.g. `sim_129`) show a parked/stationary GNTest phase firing repeated p2-p3 bullets; our old name-gated `gntestStopDuel()` excluded `stationaryShooter()`, so generic stationary/slow/fixed-heading branches could keep p3/heavy trades while GNTest landed accurate medium/high bullets.
- Updated `robots/custom/MyTank.java` with a broader name-gated `gntestEnemy()` helper and stationary GNTest handling:
  - sidestep/diagonal escape on *every* detected GNTest shot while it is parked (not only drops >2.2);
  - use a 365/420/465 preferred range band for parked GNTest instead of TrackFire p3 farming or generic stationary shooter behavior;
  - cap parked GNTest bullet power after all generic branches to faster medium/cheap tiers, with a capped lethal finisher, preventing generic p3 slow-target boosts from reappearing in the loss mode;
  - allow `gntestStopDuel()` to include stationary parked phases (still strictly name-gated).
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `kcanida__pikachu`):
- `/logs/rounds/0` aggregate is a score win (`29645` vs `17169`), but trace survival is poor: `tools/analyze_pikachu.py /logs/rounds/0` reports about 122 live wins / 128 losses. Pikachu fires very many tiny/weak bullets (loss avg enemy drop ~0.53) and makes short stop/turn dodges; our old generic branches spent ~50 shots at ~power 1.9-2.0 and often self-disabled while Pikachu retained ~50 energy.
- Offline replay of shot opportunities shows head-on / very damped averaged aim is best, and faster sub-power-1 bullets reduce future-position error (p0.1 head/damped ~34px vs p2 ~44-46px and linear/circular much worse). Losses had closer range (~218px avg) and more stopped ticks, so a wider band should also help dodge the low-power stream.
- Added a name-gated `pikachuEnemy()` branch in `robots/custom/MyTank.java`:
  - preferred range widened to ~380 healthy / 425 mid / 470 low instead of the close generic stop-go orbit;
  - gun forced to head-on or very-damped averaged; `predictEnemy()` damped averaged to `0.20*velocity + 0.20*EMA` capped at ±1.2, no turn;
  - power capped aggressively (healthy ~1.1-1.35, mid ~0.75-1.0, low ~0.18-0.62, no fire below 14 energy unless Pikachu is near death) to prevent self-depletion;
  - sidestep on detected fire after reserve is below 50, and continue a larger perpendicular escape on bullet hits;
  - excluded Pikachu from the generic wave-surfing classifier so that name-gated caps/gun/range are not stolen.
- Added `tools/analyze_pikachu.py` for future quick trace summaries. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. Local battle execution cannot reproduce hidden opponent; sample bot repository was incomplete here.

Round 2 (gpt-5-5 current edit, Pikachu follow-up):
- Reviewed `/logs/rounds/1`: the round-1 Pikachu branch was a huge improvement (`45639` vs `6228`) and `tools/analyze_pikachu.py /logs/rounds/1` reports a 250/250 live sweep. Pikachu still fires many tiny bullets (~44 detected drops/game, avg p0.38), while our end energy is very safe (~86 avg) and our shots are mostly p1.0/p1.2/p1.4.
- Offline replay still favors head-on/very-damped averaged with faster bullets over full linear/circular. Since survival is now perfect and energy surplus is high, made only a tiny scoring retune in `robots/custom/MyTank.java`: while facing the name-gated Pikachu branch and our energy is >72, raise the healthy cap from about p1.1-1.35 to about p1.4-1.6. Mid/low-energy caps and the no-fire reserve guard remain unchanged, preserving the self-depletion fix from round 1.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `mgalushka__maximbot`):
- `/logs/rounds/0` was a strong aggregate win (`43090` vs `6953`) and all result files were first place, but traces had 2 live losses and 3 mutual/energy-draw-ish endings. Maximbot is a medium/fast shallow-turn mover (avg speed ~4.6, straight ~39%, low wall time) that fires repeated medium/high bullets (avg detected drop ~2.34).
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favored circular aim on actual shots (`circ` mean ~48px at old mostly-p2.7 shots; fixed-power replay had circular/linear much better than the generic Dominator head-on branch). Losses were close exchanges around ~140-210px where our bot could be routed into broader head-on/conservation logic and still eat high-power shots.
- Added name-gated `maximbotEnemy()` in `robots/custom/MyTank.java`: force `GUN_CIRCULAR`, use a compact ~320px band while healthy but perpendicular-open close range, sidestep on detected fire, use strong-but-not-max bullets (~2.25-2.75 healthy, medium/cheap when low), skip the generic active-high-power cap, and preserve a no-fire reserve below 10 energy unless Maximbot is near death.
- Added `tools/analyze_maximbot.py` for quick future summaries. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, Maximbot follow-up):
- Reviewed `/logs/rounds/1`: aggregate score improved (`44397` vs `15912`), but trace survival regressed versus round 0. Maximbot actual wins were concentrated in very close exchanges (loss avg distance ~160px, >90% of ticks under 250px) where it fired repeated medium/high bullets and our bot kept a compact ~320px orbit with ~p2.6 shots.
- Kept the name-gated Maximbot circular gun (`GUN_CIRCULAR`), since both round-0 and round-1 offline replay still rank circular best/near-best for this shallow-turn mover.
- Retuned `robots/custom/MyTank.java` Maximbot branch defensively:
  - widened the Maximbot preferred orbit from 320/370/430 to about 375/430/485 depending on our energy;
  - close-range escape now triggers below ~320px while healthy (~385 low) and opens to ~380/455 instead of the old 300/360;
  - on detected Maximbot fire and on actual bullet hits, use larger perpendicular escapes to cross/reopen its high-power firing line;
  - reduced healthy Maximbot bullet caps from about p2.25-2.75 to about p1.85-2.20, with lower mid/low tiers, to trade slower damage for faster bullets and less self-depletion in long close duels.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `robo_code__walls`):
- `/logs/rounds/0` is a losing matchup: `results.json` has `robo_code__walls` 19326 vs us 12492, and only 2/25 ten-round result files put us first. Trace survival was roughly 53/250 live wins, 190 losses, 7 mutual-zero/draws.
- Opponent is sample.Walls-like: ~97% of ticks near the wall, speed 8 most of the time with cardinal perimeter legs, and frequent accurate head-on/near-head-on power-2 shots (quick bullet heading parse: median angular error under 1 degree). Our old generic wall handling often used max-power shots and ~335-400px range, causing long self-depletion while its p2 gun landed.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favored full linear prediction (`lin` mean ~112px) over circular (~113), averaged (~125), wall-damped (~148), and head-on (~170), so the fix is **not** wall-damped aiming.
- Added name-gated `sampleWallsEnemy()` for `robo_code__walls` in `robots/custom/MyTank.java`:
  - force `GUN_LINEAR`;
  - use wider orbit (~455 healthy, 500/535 lower energy) and large perpendicular escapes on every detected shot / bullet hit;
  - cap bullet power after all generic wall branches to faster medium shots (about 1.85-2.1 high energy, 1.05-1.35 mid, cheap low, capped lethal finisher) instead of inheriting p3 dangerous-wall/fast-wall pressure;
  - stop firing below 10 energy if Walls still has >12, preserving movement reserve rather than self-disabling with pinpricks.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. No local hidden-opponent battle is available, so this is trace-analysis based.

Round 2 (gpt-5-5 current edit, Walls follow-up):
- Reviewed `/logs/rounds/1`: the name-gated `robo_code__walls` branch flipped the matchup from losing to winning (`28700` vs `10243`), but traces still show ~177/250 live wins and many long self-depletion losses. Losses commonly had our bot at ~10 energy with Walls still >12-25; tiny reserve bullets could not finish before an already-fired p2 bullet hit.
- Added `tools/analyze_walls.py` for quick Walls trace summaries (win/loss counts, distances, shot/drop estimates).
- Retuned `robots/custom/MyTank.java` for sample.Walls only:
  - detected Walls shots no longer call the generic `reverseDirection()` first; instead `driveSampleWallsEscape()` keeps a consistent lateral dodge side and scores perpendicular crossing + separation. Actual bullet hits still flip side, then use the same longer escape.
  - widened preferred Walls range slightly (475/520/560) and tightened low-energy caps/no-fire reserve (below ~14 energy if Walls is not near lethal) to reduce remaining self-disable losses.
  - kept the replay-best `GUN_LINEAR` and healthy medium-power caps; this is a survival/movement/reserve tweak, not a gun change.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.


Round 1 (gpt-5-5 current edit against `pez__wallspoetas`):
- `/logs/rounds/0` is a Wallspoet variant (`pez__wallspoetas`) with persistent wall/stop-go movement, near-zero turns, and mostly power-3 fire. We won aggregate (`25711` vs `9744`) but survival was only 199/250 with 46 opponent wins and 5 ties. Losses were long rounds: our bot fired 50-70 mostly sub-p1 bullets after an early downshift, often dying with Wallspoetas still at 20-70 energy; offline replay still favors `GUN_AVERAGED` strongly (avg mean ~67px vs wallavg ~82, linear/circular ~89, head ~103).
- Retuned the existing Wallspoet branch in `robots/custom/MyTank.java` for this harder variant: raw detection engages earlier and avoids being blocked by fixed-heading/Juggernaut predicates; sticky counter is stronger; detected p3 fire now gets a dedicated perpendicular escape before any generic reverse; low-energy close range also reopens; `onHitByBullet` uses a larger Wallspoetas escape.
- Adjusted Wallspoet range/power: slightly closer healthy/mid orbit (370/425/485) for faster averaged shots, stronger midgame bullets (roughly p1.1-1.5 instead of early p0.3 pinpricks), capped lethal/near-lethal finishers below 16 enemy energy, and stricter reserve no-fire below 12 energy unless Wallspoetas is near death. Added a small Wallspoetas averaged-predictor velocity carry (cap 3.0) to avoid lagging behind resumed wall bursts.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, Wallspoetas follow-up):
- Reviewed `/logs/rounds/1`: the Round-1 Wallspoetas retune regressed badly versus the previous baseline (`23370` vs `25711` score, traced live wins down to ~163/250 from ~204/250, with several draws). Offline replay still says normal `GUN_AVERAGED` is the best aim family, but the closer orbit/stronger p1.5-p2.1 pressure and new fire-tick escapes appear to increase self-depletion / p3 hit exposure.
- Rolled `robots/custom/MyTank.java` back to the prior Wallspoet baseline from before the Round-1 Wallspoetas commit (the code that produced `/logs/rounds/0` score `25711` vs `9744`). This preserves the older wider 390/450/500 Wallspoet range and more conservative p0.3-p1.85 power caps, plus the existing averaged-gun Wallspoet branch and Shreker/fixed-line exclusions.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. Future teammate: if continuing this matchup, test changes against both `/logs/rounds/0` and `/logs/rounds/1`; the last aggressive Wallspoetas patch was a clear regression, so prefer small A/B retunes or a local harness if available.

Round 1 (gpt-5-5 current edit against `pez__leachpmc`):
- `/logs/rounds/0` shows a stationary active shooter. We won aggregate (`42136` vs `6176`) and every 10-round result file, but LeachPMC lands many power-3 bullets while sitting still; traces show it never moves (`stop frac 1.0`) and fires almost exclusively p3 (1775 detected p3 drops). Our old stationary farming mode could stop/slow early or orbit around ~330-455px, giving its head-on p3 stream repeated hits.
- Added a narrow name-gated `leachPmcEnemy()` branch in `robots/custom/MyTank.java`:
  - disables the no-fire stationary stop/farm mode for this opponent;
  - on detected p3 fire, immediately uses the diagonal `driveStationaryHeavyEscape()` with a wider target distance;
  - prefers a wider ~490/535px stationary orbit even before the generic stationary-heavy detector is fully settled.
- Gun/power remain exact head-on max-power for stationary targets, so kill speed should stay high while reducing p3 hit leakage. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, LeachPMC follow-up):
- Reviewed `/logs/rounds/1`: still a 249/250 win plus one tie against stationary p3 shooter `pez__leachpmc`; average score regressed slightly from round 0. The only draw (`sim_62`) was a close spawn: our bot drove into/overlapped the stationary enemy at ~37px and stayed in `HIT_ROBOT`, taking repeated 0.6 ram drops plus point-blank bullets until both died.
- Added a narrow LeachPMC close-overlap escape in `robots/custom/MyTank.java`: below ~105px it immediately drives straight forward/back along the current body axis in the direction that increases separation, rather than trying to rotate to a perfect absolute escape angle while collision events cancel movement. `onHitRobot` uses the same emergency escape for LeachPMC.
- For LeachPMC close stationary targets, prefer separation-biased `driveAwayFrom()` instead of the perpendicular fallback that pinned the round-1 draw. Slightly relaxed LeachPMC wide orbit from 490/535 to 455/505 to recover some normal-range kill speed while keeping wider-than-generic p3 dodging.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `txeverson__crawler`):
- `/logs/rounds/0` was already a 250/250 live sweep with aggregate `42967` vs `5732`; our bot averaged ~84 min energy and ~101 end energy, so the matchup is very safe.
- Trace/offline replay shows Crawler is a predictable medium-speed circular mover: avg speed ~4.74, low stop fraction (~3%), avg enemy fire drop ~p2.0, and `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favors circular aim (`circ` mean ~35px, `avg` ~36px, head-on ~74px, linear ~155px). Our old code still won, but mostly through generic/cold-start behavior and many p3 shots; name-gating lets us use the exact circular gun immediately.
- Added a narrow `crawlerEnemy()` name gate in `robots/custom/MyTank.java` for `txeverson__crawler`: forces `GUN_CIRCULAR`, uses a slightly tighter 285px healthy orbit (335 reserve), and keeps max-power pressure while energy is healthy to improve score/kill speed. Low-energy caps are only defensive for unexpected outlier rounds.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit against `txeverson__crawler`, follow-up):
- Reviewed `/logs/rounds/1`: still a 250/250 live sweep, but aggregate score regressed from round 0 (`42967` -> `42591`) after the first Crawler name-gated tweak. Bullet damage/bonus dropped; survival was still perfect and end energy was very high.
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` still strongly favors circular aim (`circ` mean ~34.8px, averaged ~35.5, head/linear much worse), so kept Crawler as a circular-family target.
- Partially rolled back the over-aggressive Crawler specialization in `robots/custom/MyTank.java` toward the successful SpinBot-style branch: preferred orbit is now ~305/340 instead of 285/335, healthy max-power range is capped at <680 with p2.55 farther (instead of p3 out to 700/p2.65 to 780), and the gun can choose damped averaged if virtual waves show a clear margin over circular. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `sacdalance__robrrrat`):
- `/logs/rounds/0` shows a much stronger active opponent, `sacdalance__robrrrat.MyTank`. Aggregate was still a win (`39461` vs `5889`, first in all 25 result files), but traced live outcomes were 246/250 wins with 4 Robrrrat wins. Opponent is mixed fast/stop movement (avg speed ~5.7, ~19% stopped, low wall time) and fires frequent mostly power-3 bullets with decent head-on/near-head-on aim (rough parse median angular error ~6 deg).
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favored normal averaged/circular prediction (`avg`/`circ` mean ~83-84px) over linear/head-on (~117/122px). Losses were long energy-depletion rounds where our bot often died with Robrrrat still at 17-64 energy after eating p3 streams and/or spending too much on slow heavy shots.
- Added a narrow name-gated `robrrratEnemy()` branch in `robots/custom/MyTank.java`: force `GUN_AVERAGED`, widen preferred orbit to ~395/470/520 by energy, sidestep/reopen on detected fire and bullet hits, and cap bullets to faster medium powers (~p2.0-2.35 healthy, low-power reserve, capped lethal finishers). Also disables last-reserve pinpricks below 12 energy unless Robrrrat is near death.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. No local hidden opponent harness is available; this is trace-analysis based.

Round 2 (gpt-5-5 current edit against `sacdalance__robrrrat`, follow-up):
- Reviewed `/logs/rounds/1`: previous Robrrrat branch regressed badly vs round 0 (`36678` vs prior `39461`; traced live wins fell from ~245/250 to ~237/250). Losses were not safer long-distance survivals; they averaged closer range (~265px) and many low/medium shots (~p1.5) while Robrrrat kept firing mostly p3 and often survived with 40-80 energy.
- Re-ran `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'`: after the round-1 behavior, circular prediction is clearly best on actual opportunities (`circ` mean ~65.6px vs averaged ~74.8, linear/wallavg ~90, head-on ~113). Round-0 was avg/circ about tied.
- Added `tools/analyze_robrrrat.py` for quick win/loss summaries of Robrrrat traces.
- Retuned `robots/custom/MyTank.java` Robrrrat handling as a partial rollback/aggressive fix:
  - preferred distance back closer to the high-scoring baseline (~345 healthy, 420 mid, 500 low) instead of the wider 395/470/520 band, plus a knife-range perpendicular escape below ~235-285px;
  - healthy/mid bullet caps raised substantially (p3 under ~520 while energy >62, p2.55 farther; p1.65-2.05 mid) because the p1-ish conservation was prolonging losing rounds;
  - gun now defaults to `GUN_CIRCULAR`, allowing averaged only if virtual waves show a clear margin. This matches round-1 replay and should recover hit rate/kill speed while keeping low-energy reserve guards.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `pez__haikuwalls`):
- `/logs/rounds/0` is now a losing matchup: `pez__haikuwalls.MyTank` won aggregate `23218` vs our `12477`, with per-game survival about HaikuWalls 150 wins, us 92, 8 draws. It is a PEZ/sample-Walls-family perimeter runner: ~91% of active ticks within 40px of a wall, average speed ~5.5 with long straight border legs, and it fires almost all power-3 bullets (~28 detected shots/game).
- Offline replay (`tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'`) and an actual-shot replay both showed full linear prediction is best (actual-shot mean error ~127px; averaged ~158; head-on ~206; wall-damped ~177). Lower-power/faster bullets also have much lower future-position error than p3 against the border runner.
- Added a narrow name-gated `haikuWallsEnemy()` profile in `robots/custom/MyTank.java`:
  - forces `GUN_LINEAR`;
  - caps bullet power aggressively (~0.9-1.2 while healthy, lower in reserve, capped lethal finishers) so shots are faster and we stop self-depleting into its p3 stream;
  - widens preferred orbit to ~525-625 and uses the sample-walls perpendicular/away escape on each detected shot and bullet hit;
  - tightens fire tolerance for this matchup.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, HaikuWalls follow-up):
- Reviewed `/logs/rounds/1`: the round-1 name-gated `pez__haikuwalls` branch flipped the matchup from losing to winning (`20478` vs `10879`, ~226/250 live wins), but remaining 24 losses are long power-3 wall-runner duels. Loss traces usually have us low on energy while HaikuWalls still has 20-50 energy; our bot then fires many 0.2-0.6 reserve bullets that cannot finish before a p3 hit lands. Some losses also had HaikuWalls under ~10-18 energy where a stronger finisher would likely help.
- Kept the proven HaikuWalls `GUN_LINEAR`, wide sample-walls escape movement, and low/medium healthy caps. Tiny endgame retune in `robots/custom/MyTank.java`:
  - expanded HaikuWalls capped-lethal finisher from enemy energy <8 to <18 (cap ~2.15, ~1.85 below 10) while we have >9 energy and range <650;
  - added a stricter no-fire reserve guard below 24 energy when HaikuWalls still has >20 energy, preserving movement energy instead of donating weak pinpricks in hopeless long chases;
  - existing <14 energy / enemy >9 no-fire guard remains.
- Added `tools/analyze_haiku.py` for quick HaikuWalls trace summaries. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `pez__smallpoet`):
- `/logs/rounds/0` shows a hard PEZ wall/poet variant. We still won aggregate (`23198` vs `17737`) and 19/25 ten-round battle files, but traced live survival was only about 175 wins / 70 losses / 5 draws. SmallPoet is wall/stop-go/straight mixed (~64% wall, ~40% stopped, ~46% straight ticks) and fires almost exclusively power-3 bullets; losses are p3/self-depletion rounds, often with it surviving on 40+ energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favored damped averaged/wallavg (wallavg mean ~71px, normal avg ~72.5; linear/circular/head-on worse overall). A quick replay suggested a slightly less damped averaged predictor (`0.35*current + 0.50*EMA`, cap 2.8, no turn) is marginally better than the old Wallspoet damping for this specific mover.
- Added name-gated `smallPoetEnemy()` in `robots/custom/MyTank.java`: force `GUN_AVERAGED` with that custom damping; use a wider 430/500/555 range band; sidestep/away using `driveSampleWallsEscape()` after p3 fire once in close/mid danger or below ~56 energy; cap bullets to faster medium-low powers (healthy ~1.25-1.65, mid <=1.05, low pinpricks) with capped lethal finishers; and stop last-reserve firing when SmallPoet still has a large energy stack.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. Future teammate: compare against both SmallPoet and prior Wallspoet/Wallspoetas logs; this is name-gated to avoid repeating the Wallspoetas regression.

Round 2 (gpt-5-5 current edit, SmallPoet follow-up):
- Reviewed `/logs/rounds/1`: the first name-gated `pez__smallpoet` branch improved substantially over round 0 (`25631` vs `23198`, live wins 220/250 vs 175/250), so kept its wide-range averaged-gun/conservative-power profile.
- Remaining losses/ties mostly occur with SmallPoet already in the 10-18 energy band while our bot has ~9-15 energy. The previous reserve guard could stop firing below 13 energy whenever SmallPoet had >10, leaving it alive to land another p3; capped finishers were also limited to ~2.05 power for 14-18 energy targets.
- Tiny endgame retune in `robots/custom/MyTank.java`: for SmallPoet only, capped lethal/near-lethal finishers are stronger for enemy energy 10-18 (cap 2.55/3.0 (and 2.05 below 10) depending on remaining energy), and the last-reserve no-fire guard below 13 energy now only blocks if SmallPoet is still above 18 energy. The broader low-energy reserve guard (`our energy <22 && enemy >22`) is unchanged.
- Added `tools/analyze_smallpoet.py` for future quick win/loss summaries of SmallPoet traces. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `gjgomez__mb2`):
- `/logs/rounds/0` opponent is a real medium-speed mixed stop/turn bot, `gjgomez__mb2.MyTank`. We swept all 250 traced games and all 25 result files (aggregate `40313` vs `3209`), but score per 10-round set is only ~1600: MB2 survives ~540 ticks, moves ~3.2 avg / 4.67 max, stops ~19%, turns often, and fires almost exclusively weak power-1 bullets (~24/game). Our end energy is high (~80), so matchup is safe but kill speed/hit efficiency can improve.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` and a quick all-tick replay favored the damped `wallavg`/averaged predictor over full linear/circular/head-on for MB2, especially with faster medium bullets (p1.5-p2.2) instead of slow p3 over-leads.
- Added a narrow name-gated `mb2Enemy()` branch in `robots/custom/MyTank.java`: compact ~330px healthy orbit, forced `GUN_AVERAGED` with wallavg-style damping (`0.25*current + 0.35*EMA`, no turn), medium-power caps while healthy, and low-energy reserve guard. This should preserve the guaranteed sweep while trying to shorten rounds / raise bullet damage against MB2 without disturbing prior opponent-specific profiles.
- Added `tools/analyze_mb2.py` for quick movement/fire summaries. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, MB2 follow-up):
- Reviewed `/logs/rounds/1`: MB2 score improved after the first name-gated branch (`40770` vs round-0 `40313`) and survival is effectively perfect (249 wins + 1 mutual-zero/tie; avg our end energy ~89). MB2 remains a safe weak power-1 shooter with medium-speed stop/turn movement.
- Re-ran `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'`; averaged/wallavg are best overall. A shot-time replay over our actual fire ticks showed the previous MB2 wallavg damping (`0.25*current + 0.35*EMA`, cap 2.2) under-led continued rolls; a less-damped averaged projection (`0.45*current + 0.65*EMA`, cap 3.5) had much lower mean future error (~49px -> ~36-39px at p0.7-p1.0, and was also best around our p1.8-p2.2 shots).
- Updated only the name-gated `mb2Enemy()` averaged predictor in `robots/custom/MyTank.java` to use that less-damped velocity carry, while leaving the safe orbit/power caps from round 1 intact.
- Fixed `tools/analyze_mb2.py` so it accepts either a glob or a round directory path. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `miradoconsulting__roleksii`):
- `/logs/rounds/0` is a close active matchup. We won aggregate (`25023` vs `18100`) and 20/25 ten-round result files, but traced live outcomes were only about 174 wins / 72 losses / 4 draws. Opponent is a wall/stop-go/low-turn mover (~56% near wall, ~34% stopped, avg speed ~3.6) that fires frequent power-3 bullets; many losses show us eating p3 streams or self-depleting while it survives with 20-90 energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favors normal averaged prediction (`avg` mean ~51px) over linear/circular (~61) and head-on (~102); low/medium bullet powers have much lower replay error than p3.
- Added a narrow name-gated `roleksiiEnemy()` branch in `robots/custom/MyTank.java`: force averaged gun with moderate damping, use Haiku/SampleWalls-style perpendicular+away escape on every detected shot, widen the preferred orbit to ~520-635, cap bullets to fast medium/cheap powers with capped lethal finishers, tighten aim tolerance, and stop last-reserve pinpricks when Roleksii still has a large energy stack.
- Also added a broader `wallsPoetEnemy()` fire-tick escape (milder than Roleksii) so p3 wall/stop-go poets cross the firing line instead of only reversing orbit. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, Roleksii follow-up):
- Reviewed `/logs/rounds/1`: score improved from round 0 (`29046` vs `25023`) and traced survival improved to 235/250, but remaining losses still showed `miradoconsulting__roleksii` surviving with large energy after repeated p3 fire. Loss traces had much more close-range exposure (about 24% of live ticks under 250px vs 11% in wins) and sometimes left Roleksii in the 10-18 energy band while our old reserve guard blocked finishers.
- Kept the name-gated Roleksii averaged gun / wide p3-dodge concept, but made it more consistent:
  - Roleksii movement preference is now checked before generic fixed-heading/high-power stop-go branches, preventing those classifiers from stealing the matchup and pulling range back to ~300px.
  - Added a close-range Roleksii `driveAwayFrom()` escape when distance collapses below ~340/430px.
  - On actual bullet hits, Roleksii now switches dodge side and takes a full sample-walls-style escape.
  - Expanded capped lethal/near-lethal finishers to enemy energy <18 and relaxed/skipped overlapping low-energy no-fire guards so those finishers are not blocked.
  - Offline coefficient replay over `/logs/rounds/1` favored a slightly less-damped averaged predictor (`0.45*current + 0.65*EMA`, cap 3.5), especially on loss traces, so Roleksii now uses that.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `logancsc__dodgebot2`):
- `/logs/rounds/0` is a close active matchup. We won aggregate score (`24341` vs `21236`) but only 13/25 ten-round result files; traced live outcomes were nearly even (121 wins / 123 losses / 6 draws). Opponent is a full-speed evasive mover (max 8, avg speed ~4) that fires frequent weak/medium bullets (energy-drop avg ~1.49). Our accuracy was higher (28% vs 20%) but we often self-depleted with heavier shots in long close exchanges.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favored head-on (mean ~57px) over wallavg (~78), averaged (~92), circular (~104), and linear (~115) for DodgeBot2. A quick power replay also showed faster low/medium bullets reduce future-position error versus p3.
- Added a narrow name-gated `dodgeBot2Enemy()` branch in `robots/custom/MyTank.java`: force `GUN_HEAD_ON`, use a somewhat wider 365/405/455px preferred range, block the generic hard-to-hit closer-orbit override, cap bullets to faster medium/cheap powers with lethal finishers, tighten fire tolerance, stop last-reserve pinpricks when DodgeBot2 is still healthy, on detected fire ticks below 48 energy take a perpendicular escape instead of just reversing orbit, and after actual bullet hits take a larger lane-changing escape.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. This is conservative/name-gated; next teammate should compare `/logs/rounds/1` to see if the lower-power head-on profile improved survival/score or became too timid.


Round 3 (gpt-5-5 current edit, DodgeBot2 follow-up):
- Reviewed `/logs/rounds/1`: the round-1 DodgeBot2 head-on/cheap-bullet profile was a large improvement (`34490` vs `14672`, all 25 ten-round result files won, traced live ~208 wins / 36 losses / 6 mutual-active timeouts). Kept the proven forced `GUN_HEAD_ON`, medium-low power caps, and wider 365/405/455 orbit.
- Remaining losses clustered in long close scrambles: loss traces averaged closer range than wins, had ~22% of ticks under 180px, and several ended with DodgeBot2 in the 10-18 energy band while our bot was spending p0.4-p0.6 reserve shots.
- Tiny name-gated retune in `robots/custom/MyTank.java`: added a close/low-energy DodgeBot2 direct separation escape (<175px healthy, <285px once under 38 energy), made `onHitRobot` separate harder from DodgeBot2, and expanded bounded near-lethal finishers for enemy energy <18 at <420px (cap 2.35, still conservative below 8 energy). This is intended to reduce the remaining close-range losses without changing the successful normal-range strategy.
- Added `tools/analyze_dodgebot2.py` for quick outcome/range/energy-drop summaries. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `pez__poet`):
- `/logs/rounds/0` shows a strong aggregate win (`38200` vs `7068`) and almost perfect live survival (243/250 wins, 1 live loss, 6 timeout/draw-ish traces). Poet is a fast high-speed turning PEZ bot (avg speed ~6.7, turn ~0.077 rad/tick) that fires mostly power-3. Our min/end energy is generally high, but the one loss self-depleted after a long ~427px average-distance exchange.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favors circular prediction (`circ` mean ~97px vs averaged ~103, linear/head ~143). A quick fixed-power replay suggested faster bullets are geometrically more accurate, but given our large energy surplus and high score, the main tweak is to avoid generic hard-to-hit/wall branches stealing the matchup rather than over-conserving.
- Added a narrow name-gated `poetEnemy()` branch in `robots/custom/MyTank.java`: force `GUN_CIRCULAR`, keep a closer 285px healthy orbit (350/420 when energy falls), preserve high/max-power circular pressure while healthy, add bounded finishers under 18 enemy energy, and use cheap bullets only in rare low-energy reserves. Detected fire / bullet hits trigger a perpendicular escape only once energy is lower or range is too close, so normal high-scoring circular farming remains aggressive.
- Excluded Poet from the generic hard-to-hit 355px override and Crazy power block so the new circular profile owns movement/power. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, Poet follow-up):
- Reviewed `/logs/rounds/1`: the `pez__poet` branch remains a perfect live sweep (250/250 traced wins, all result files 10/10). Aggregate score is essentially flat/slightly lower vs round 0 (`37940` vs `38200`) but opponent bullet damage fell a lot (`~184` vs `~283` per 10-round battle) and our end energy is very high (~91), so the profile is safe.
- `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` still favors circular aim clearly (`circ` mean ~103 vs averaged ~108, head/linear ~149/152). Shot-time replay over our actual fire ticks also favored circular for the dominant p3 shots.
- Added `tools/analyze_poet.py` for quick future summaries of Poet traces/result files.
- Tiny score/kill-speed retune in `robots/custom/MyTank.java`: for name-gated Poet only, if Poet is below 18 energy and our bot still has >28 energy, allow a true p3 bounded finisher instead of the old 2.6 cap. Round-1 traces had huge energy reserve and no losses, so this should reduce extra final shot cycles without changing movement/gun behavior. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `pez__wallspoethaiku`):
- `/logs/rounds/0` is a moderate win but not safe: aggregate `20516` vs `15864`, 18/25 ten-round result files won, traced live survival about 157 wins / 90 losses / 3 draws.
- Added `tools/analyze_wallspoethaiku.py` (quick trace summary with outcome/range/fire stats and worst-loss details). Losses are not classic stopped Wallspoet losses: the opponent is much faster in losses (avg speed ~7.0, ~79% full-speed ticks, only ~6% stopped), wall-heavy (~46% near wall), and fires mostly p3 (~27 shots/loss). Our old branch averaged p2-ish shots and self-depleted; offline replay strongly favors full linear prediction (`lin` mean ~115 at actual old shots vs circ ~117, avg ~136, head ~189; fixed-power replay shows p0.7-p1.1 linear is much more accurate than p2-p3).
- Added a narrow name-gated `wallspoetHaikuEnemy()` profile in `robots/custom/MyTank.java`:
  - force `GUN_LINEAR` (instead of generic Wallspoet averaged);
  - HaikuWalls-like wide orbit (~500/560/620) and sample-walls-style escape on every detected fire / bullet hit;
  - low/medium-fast linear bullets while healthy (about p0.9-1.15, p0.5-0.8 mid/low) with bounded finishers under enemy 18 energy;
  - tighter fire tolerance and low-energy no-fire guard against still-healthy p3 stack;
  - excluded from generic hard-to-hit range override.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. Next teammate: compare `/logs/rounds/1` to see if low-power linear/wide dodge improves survival enough; if too timid (low bullet damage while still losing), consider p1.3-1.6 healthy or slightly closer 470/535 range.

Round 2 (gpt-5-5 current edit, WallspoetHaiku follow-up):
- Reviewed `/logs/rounds/1`: the new name-gated `pez__wallspoethaiku` profile was a huge improvement over round 0 (`28176` vs `20516` aggregate; live survival about 245/250 vs 157/250). Linear gun + wide orbit + low/medium bullet caps are clearly the right direction, so I kept that core.
- Remaining 5 losses were corner/close-collapse or endgame cases: min distances often dropped below ~90px and we ate multiple p3 hits; in some losses WallspoetHaiku reached the 11-18 energy band while our reserve guard/low caps left it alive to land another p3.
- Tiny targeted changes in `robots/custom/MyTank.java`:
  - added an immediate close-range `driveAwayFrom()` for WallspoetHaiku when distance falls below ~310px (healthy) / ~390px (low energy), opening to ~385/470 instead of waiting for the next fire tick;
  - expanded WallspoetHaiku near-lethal finishers: enemy energy <18 can now use capped ~2.55 (or ~1.95 below 10) when we have >9 energy and range <650;
  - relaxed the no-fire reserve guard so it no longer blocks that <18-energy finisher band (still preserves energy when we are <24 and the enemy is >24, or <14 and enemy >18).
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `alexjamesmacpherson__wilde`):
- `/logs/rounds/0` is the first recorded losing aggregate for this branch: `alexjamesmacpherson__wilde.MyTank` beat us `14578` vs `12340`; traced live outcomes were about 69 wins / 176 losses / 5 draws. Wilde is a medium/fast wall-heavy stop/go mover (avg speed ~4.4, ~46% wall-near, ~19% stopped) firing frequent weak/medium bullets (energy-drop avg ~1.34). Our bot often self-depleted to 0 after 70-100 shots while Wilde still had a large reserve.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favors the damped averaged/wallavg family (`wallavg`/`avg` clearly ahead of head/linear/circular), and lower-power/faster bullets are important because rounds are long and p2/p3 misses drain us.
- Added a narrow name-gated `wildeEnemy()` profile in `robots/custom/MyTank.java`:
  - force `GUN_AVERAGED` with a Wilde-specific damped predictor (`0.32*current + 0.48*EMA`, no turn, capped at 2.6);
  - hold a medium-wide 425/475/515 preferred range and exclude Wilde from the generic hard-to-hit close-orbit override;
  - cap bullet power aggressively (roughly p1.1-1.35 while high energy, p0.68-0.95 mid, p0.26-0.38 reserve) with bounded lethal finishers under 12 enemy energy;
  - once our energy is lower, dodge across its fire line on detected shots / actual bullet hits and stop last-reserve pinpricks when Wilde is still healthy.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. Next teammate: compare `/logs/rounds/1` to see if this conservative profile improves survival/aggregate or became too timid; if bullet damage collapses while survival does not improve, raise healthy/mid caps toward p1.5/p1.1 or tighten range slightly (~390/440).

Round 2 (gpt-5-5 current edit, Wilde follow-up 2):
- Reviewed `/logs/rounds/1`: the first name-gated Wilde profile flipped the matchup to a score win (`17309` vs `12566`) but traced live outcomes are still roughly even (~119 wins / 120 losses / 11 draws). Losses remain self-depletion/endgame issues; our average shot power fell to ~0.8, but some traces show abrupt zero-energy endings after long generic gun processing / broad branch interactions.
- Added a dedicated early-return `doWildeGun()` in `robots/custom/MyTank.java` for `alexjamesmacpherson__wilde` only. It bypasses the huge generic gun/power tree so Wilde cannot be re-raised by broad wall/slow branches, always uses the replay-best damped averaged predictor, keeps fast cheap bullets, adds a bounded finisher for enemy energy <16, and stops last-reserve pinpricks when Wilde is still >20 (or >8 at very low reserve).
- Movement from the previous Wilde profile is unchanged (medium-wide 425/475/515 band and low-energy perpendicular escapes). Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `pez__haikupoet`):
- `/logs/rounds/0` is a losing aggregate: `pez__haikupoet` beat us `22378` vs `16918`; per-trace live outcome about 109 wins / 128 losses / 13 mutual-zero draws. HaikuPoet is wall-heavy (~56% near wall), stop/go (~40% stopped, ~27% full-speed), and fires steady p2-ish bullets (~38 shots/game, avg drop ~2.1). Our generic profile stayed ~430-440px, fired too many p1-p2 shots, and many losses were self-depletion with HaikuPoet still 20-60 energy.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` favored the damped wallavg/head-on family (`wallavg` mean ~91, head ~92, avg ~95; linear/circular ~104-105). This differs from WallspoetHaiku/HaikuWalls linear specializations.
- Added a narrow `haikuPoetEnemy()` name-gated profile in `robots/custom/MyTank.java`:
  - early-return `doHaikuPoetGun()` bypasses the huge generic gun tree, forces damped `GUN_AVERAGED`, uses cheap/fast bullets (roughly p0.95-1.25 high energy, p0.56-0.78 mid, p0.1-0.36 reserve) with bounded finishers under enemy 18 energy, and suppresses last-reserve pinpricks when HaikuPoet is still healthy;
  - movement holds a wide ~500/555/610 lane, uses sample-walls-style crossing on every detected enemy shot / bullet hit, and directly reopens range if it collapses below ~315-390;
  - HaikuPoet is excluded from the generic hard-to-hit close-orbit override. Predictor damping is `0.25*current + 0.35*EMA`, cap 2.2, turn 0.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, HaikuPoet follow-up):
- Reviewed `/logs/rounds/1`: the first HaikuPoet profile flipped the match to a win (`19556` vs `13479`) and improved live outcomes to about 200 wins / 41 losses / 9 draws, but losses still self-depleted while `pez__haikupoet` kept ~30+ energy. Round-1 result composition traded much higher survival for lower bullet damage (our avg shot power ~0.94).
- Added `tools/analyze_haikupoet.py` for quick outcome/range/shot summaries.
- Offline actual-shot replay over `/logs/rounds/1` showed pure head-on now beats the damped averaged gun on this low-power wide-lane profile (head-on mean error ~95px vs damped ~114px; stopped ticks are especially favorable). Updated `doHaikuPoetGun()` to force `GUN_HEAD_ON` while retaining the name-gated early-return gun tree.
- Retuned HaikuPoet endgame/movement slightly:
  - low-reserve close escape now keeps reopening until ~520px when our energy is below 34, instead of resuming orbit around 390px;
  - mid-low bullet caps were raised modestly (p0.48-0.65 above 24 energy, p0.22-0.32 above 14) so we do not spend dozens of p0.1-p0.3 pinpricks while HaikuPoet remains healthy;
  - no-fire reserve guard now starts below 20 energy when HaikuPoet is >24, preserving the final reserve but allowing the new mid-low pressure.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 1 (gpt-5-5 current edit against `lucasgch__bt7274`):
- `/logs/rounds/0` shows a real fast mover/shooter. We won every 10-round battle (`results.json` 36589 vs 4712), but BT7274 fires mostly power-3, moves at/near max speed with mixed straight/turn motion, and scored meaningful bullet damage (e.g. result_0: 272 bullet dmg). Our end energy was usually high, but a handful of traces ended low/dead and the prior generic Crazy/Dominator/hard-to-hit branches could pull us into close p3/circular exchanges.
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` ranked averaged/circular close on actual old shots, but a shot-time power replay suggested faster medium bullets greatly reduce future-position error versus p3. I added a narrow `bt7274Enemy()` name-gated profile in `robots/custom/MyTank.java`:
  - force the damped `GUN_AVERAGED` predictor rather than generic circular/head-on/dominator overrides;
  - use a medium-wide orbit (~425 healthy, widening as energy drops) and cross the firing line on detected shots;
  - cap healthy bullet power around 1.55-1.95 with low-energy reserve/finishers, excluding this bot from generic hard-to-hit close-orbit and Crazy p3 power boost.
- Recompiled successfully: `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, BT7274 follow-up):
- Reviewed `/logs/rounds/1`: the first name-gated `lucasgch__bt7274` conservative profile was a clear regression, flipping the matchup from a large round-0 win (`36589` vs `4712`, ~246/250 live wins) to a score loss (`16749` vs `22952`, only ~110/250 live wins in quick trace parsing). It widened range, forced damped averaged aim, and capped bullets too low; losses showed our bot self-depleting while BT7274 kept ~40+ energy and continued p3 fire.
- Rolled back that specialization by disabling `bt7274Enemy()` in `robots/custom/MyTank.java`. This lets BT7274 fall back to the proven generic fast-mover handling from round 0 (virtual/circular/averaged guns, closer range, heavier bullets) instead of the round-1 medium/cheap profile.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 3 (gpt-5-5 current edit, BT7274 follow-up 2):
- Reviewed `/logs/rounds/2` after the Round-2 rollback disabling the bad `bt7274Enemy()` specialization. The matchup is back to a strong generic-fast-mover win: `results.json` is `36832` vs `3793`, all 25 ten-round result files first place, and trace outcomes show no opponent live wins (longest traces are low-energy/time-limit-ish wins/active endings).
- Quick trace scan: BT7274 is a fast high-turn mover (avg abs speed ~6.65, avg turn ~0.063 rad/tick), fires repeated high-power bullets (~12/game avg p2.8), and our generic Crazy/virtual handling mostly fights close (~273px avg) with heavy shots (~p2.68). `tools/offline_gun_eval.py '/logs/rounds/2/sim_*.jsonl'` ranks averaged/circular close (`avg` mean ~100px, `circ` ~102), so forcing a name-gated gun is not worth the regression risk after the failed wide/cheap profile.
- No code changes this round beyond this note; keeping `bt7274Enemy()` disabled intentionally preserves the proven generic close/heavy fast-mover behavior. If a future teammate experiments, try only tiny A/B changes (e.g. virtual-gated averaged vs circular) without changing range/power, and compare against `/logs/rounds/1` and `/logs/rounds/2`.

Round 1 (gpt-5-5 current edit against `denssle__megaborsten`):
- `/logs/rounds/0` is already a strong aggregate win (`29780` vs `8510`) with all result files won, but traced live outcomes had 28 losses / 5 draws out of 250. Losses were mostly self-depletion: Megaborsten often survived with 30-80 energy after our generic fast-wall tree spent p2/p3 (avg our shot/drop ~1.73).
- Added `tools/analyze_megaborsten.py` for quick summaries. Opponent profile: fast wall/perimeter runner (~74% full-speed ticks, ~45% near-wall, only ~10% stopped) firing mostly p1/p1.5 with occasional p3. `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favored full linear aim (mean ~91px) over circular (~101), averaged (~116), wallavg (~123), head-on (~150); fixed-power replay showed faster p0.7-p1.3 linear bullets are geometrically much better than p2/p3.
- Added a narrow name-gated `megaborstenEnemy()` profile in `robots/custom/MyTank.java`: early-return `doMegaborstenGun()` with forced linear aim, medium/fast bullet caps (healthy p1.45-1.75, mid p0.95-1.20, low p0.45-0.65, bounded finishers under enemy 18), last-reserve no-fire guard, moderate-wide 400/460/520 orbit, sample-walls-style escape on detected fire/bullet hit, and close-range direct separation. Excluded it from the generic hard-to-hit 355px override. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.
- Next teammate should compare `/logs/rounds/1` to round 0. If bullet damage/kill speed collapses, raise healthy caps toward p2.0 or tighten orbit to ~375/435; if survival improves but score slips only slightly, keep the safer profile.

Round 2 (gpt-5-5 current edit, Megaborsten follow-up):
- Reviewed `/logs/rounds/1` after the new name-gated `denssle__megaborsten` profile. It was a major improvement over round 0: aggregate score rose from `29780 vs 8510` to `37429 vs 2367`, with 249/250 traced live wins and the one remaining trace ending with a large energy advantage. Survival and bullet damage both improved sharply; average round length dropped from ~761 to ~482 ticks.
- The linear-gun/medium-power profile is clearly good. `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` still strongly favors linear prediction (`lin` mean ~83px, circular ~84, averaged ~104), and hit-event stats show most successful hits are around p1.8.
- Made only a tiny pressure retune in `robots/custom/MyTank.java`: tightened the Megaborsten preferred orbit from 400/460/520 to 365/435/500 and raised healthy/mid bullet caps slightly (p1.75/1.45 -> p1.85/1.55, p1.20/0.95 -> p1.28/1.02). This should shorten bullet flight and kill time while preserving the low-energy conservation that eliminated round-0 losses.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. If this regresses in `/logs/rounds/2`, revert just these small Megaborsten numeric tweaks to the round-1 values; keep the name-gated linear early-return profile.

Round 1 (gpt-5-5 current edit against `mgalushka__superwalls`):
- `/logs/rounds/0` shows a strong wall/perimeter opponent, `mgalushka__superwalls.MyTank`, and we lost the aggregate (`18759` vs `22339`; only 6/25 ten-round result files first for us, though traced per-game survival was close at ~130/250 wins). SuperWalls is wall-bound ~58% of ticks, fires mostly power-3, and was outscoring us on bullet damage (13301 vs 9539).
- `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` says normal averaged prediction is best on our actual shots (`avg` mean ~75px, linear/circular ~78, head-on ~89). A quick fixed-power replay showed much lower future-position error for fast low-power bullets than for p2/p3 against this target.
- Added `superWallsEnemy()` name gate in `robots/custom/MyTank.java`: wide HaikuWalls-style lane (~535-640), sample-walls escape on every detected shot/hit, forced `GUN_AVERAGED` (explicitly excluded from the generic damped wallavg predictor), and low/medium-fast bullet caps with bounded lethal finishers plus low-energy no-fire reserve.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, SuperWalls follow-up):
- Reviewed `/logs/rounds/1`: first `mgalushka__superwalls` specialization was a big improvement over round 0, flipping from a loss (`18759` vs `22339`) to a decisive win (`23156` vs `11384`, ~216/250 traced wins). Keep the wide HaikuWalls-like lane, forced `GUN_AVERAGED`, low/fast bullet caps, and sample-walls escape on detected fire/hit.
- Added `tools/analyze_superwalls.py` for future summaries. Remaining losses are split between (a) long wall-heavy chases where SuperWalls survives around 5-16 energy while our old low reserve/no-fire guard uses p0.5-p0.8 pinpricks, and (b) occasional close/corner collapses under ~90px while p3 bullets are still incoming.
- Tiny targeted SuperWalls retune in `robots/custom/MyTank.java`: direct close-range `driveAwayFrom()` when distance is <300px healthy / <390px low-energy; expanded bounded finisher for enemy energy <18 (cap 2.35, or 1.85 below 10); relaxed the no-fire guard so it preserves reserve only when SuperWalls is still >24 energy (or >18 at very low reserve), not in the <18 finisher band.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. Next teammate should compare `/logs/rounds/2`; if score regresses, revert only the new SuperWalls numeric tweaks above, not the main round-1 specialization.

Round 1 (gpt-5-5 current edit against `pmontp19__propiavancat`):
- `/logs/rounds/0` is a strong aggregate win (`39782` vs `3250`) and 25/25 ten-round files first place, but traces still had 2 live losses. Opponent is a fast max-speed turning/circular mover (avg speed ~7.54, ~90% full-speed ticks, avg turn ~0.032 rad/tick) with modest medium fire (~8.6 detected drops/game, avg p1.6). `tools/offline_gun_eval.py '/logs/rounds/0/sim_*.jsonl'` strongly favored circular prediction (`circ` mean ~61px vs linear ~87, averaged ~115, head-on ~171).
- Added name-gated `propiAvancatEnemy()` in `robots/custom/MyTank.java`: early-return circular gun, compact 310px healthy orbit (390/470 when energy falls), p3 circular pressure while high energy, low-energy reserve/no-fire guard, and bounded finishers. Also excluded it from the generic hard-to-hit 355px override and added close/low-energy perpendicular/direct escapes.
- Added `tools/analyze_propiavancat.py` for future trace summaries. Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`.

Round 2 (gpt-5-5 current edit, PropiAvancat follow-up):
- Reviewed `/logs/rounds/1`: the first name-gated `pmontp19__propiavancat` circular branch improved from round 0 (`39782` -> `41972`) and produced a clean 250/250 live sweep. Opponent is still a very fast max-speed circular/turning mover with modest medium fire; `tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'` still strongly favors circular aim (`circ` mean ~75.9 vs linear ~107, averaged ~120, head-on ~181).
- Made only tiny score/kill-speed tweaks in `robots/custom/MyTank.java`: tightened healthy PropiAvancat orbit from 310 to 295, and if PropiAvancat is in the 9-18 energy band while our reserve is high, allow a true power-3 finishing shot instead of the old 2.65 cap. Low-energy reserve behavior and circular gun are unchanged.
- Recompiled successfully with `javac -cp libs/robocode.jar robots/custom/MyTank.java`. If this regresses, revert just those two PropiAvancat numbers; keep the name-gated circular branch.
