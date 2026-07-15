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
