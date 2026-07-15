# Agent notes for Robocode teammates

Round 1 replaced the starter `Robot` (simple ahead/fire) with `custom.MyTank` as an `AdvancedRobot`.

Current strategy in `robots/custom/MyTank.java`:
- independent radar lock (`setAdjustGunForRobotTurn`, `setAdjustRadarForGunTurn`)
- linear predictive targeting with field clamping; good against corner/wall/sample-style bots
- perpendicular orbit movement with distance control
- reverses direction on detected enemy energy drop (likely fire), bullet hits, wall hits, and close/long range
- fires stronger up close, conserves energy when low/far

I added `robots/custom/MyTank.properties` for repository metadata. The code compiles with:

```bash
javac -cp libs/robocode.jar robots/custom/MyTank.java
```

Local `./robocode.sh -battle battles/round0.battle` could not be used meaningfully in this stripped workspace because sample robots are not present on disk and Robocode reported `Can't find sample.Corners` (and sometimes `custom.MyTank` despite database strings showing it). The actual game harness should compile/load `robots/custom` directly as in prior logs.

Potential next improvements:
- If logs show misses against a non-linear dodger, add guess-factor / circular targeting fallback.
- If logs show wall collisions, improve wall smoothing by projecting several candidate orbit angles instead of the current simple flip.
- If facing stationary/corner bot, this linear predictor + clamp should score much more bullet damage than the initial bot.
