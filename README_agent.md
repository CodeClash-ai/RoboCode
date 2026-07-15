# Agent notes for MyTank (Robocode)

## Status as of this round
- Round 0 logs (`/logs/rounds/0/`) show we won 250/250 sim games vs
  `technischeinformatica__tearsofsteel`, but note: every sim_*.jsonl file only lists
  ONE robot (`"robots": {"0": "sonnet_5"}`) — the opponent never appears at all.
  This strongly suggests the opponent bot failed to load/crashed on their side, and we
  won by default/walkover, NOT because our old bot (simple `ahead(400)` loop +
  `fire(1)` on scan) was actually competitive. Don't assume the old bot is good;
  it never got real combat data.
- Because of this, I rewrote `robots/custom/MyTank.java` from scratch as a proper
  `AdvancedRobot` with:
  - Radar lock-on (turns radar back toward last scanned enemy each tick, with a bit
    of overshoot so it doesn't lose lock on fast-moving targets).
  - Linear-prediction gun targeting (predicts enemy position assuming it keeps its
    current heading/velocity, iterated a few times to converge with bullet travel
    time), with bullet power scaled inversely with distance to conserve energy at
    range and hit hard up close.
  - Perpendicular "orbit strafing" movement around the enemy at ~300px preferred
    distance, with randomized periodic direction flips (to be less predictable
    against simple targeting bots) and a bias to close/open distance towards the
    preferred range.
  - Defensive reactions: `onHitByBullet` reverses strafe direction + jukes,
    `onHitWall` backs off and reverses, `onHitRobot` backs away to avoid extra ramming
    damage.
- Confirmed the new `MyTank.java` compiles cleanly with `javac -cp libs/robocode.jar
  -d robots robots/custom/MyTank.java` (no errors/warnings besides the expected ones).

## Known limitation / unresolved issue
- I was NOT able to get a local headless battle to actually run in this sandbox to
  validate combat behavior end-to-end. Repro:
  ```
  cd /workspace
  bash ./robocode.sh -battle battles/test_self.battle -nodisplay -nosound -results /tmp/r.txt
  ```
  (note: must run as `bash ./robocode.sh`, not `bash robocode.sh`, or the script's
  `cd "${0%/*}"` line fails since `${0%/*}` doesn't strip anything when there's no
  leading path component).
  Even with a from-scratch `robots/robot.database` and both robots compiled (including
  trying `--release 8` in case classfile version was the issue — it wasn't, default
  javac already emits major version 52 in this JDK, no it emitted 68 by default /
  compiles fine at 52 with --release 8 either way), robocode's repository scanner
  logs `Can't find 'custom.MyTank'` and `Can't find 'custom2.MyTank2'` and then runs
  0-robot rounds. I did not have enough remaining steps this round to dig further
  (possibly it wants a `.properties` sidecar file per robot describing
  name/author/version, or a different repository-root layout, or the headless
  environment here just doesn't support -nodisplay battle running at all). Root
  round 0 results (`/logs/rounds/0/results_0.txt`, `results.json`) show the *actual*
  grading harness DOES successfully load and run our `custom.MyTank` (it shows up as
  `sonnet_5.MyTank` — the harness likely repackages/renames `custom` -> `<player
  name>` before running), so this local-run issue is probably specific to this sandbox
  and not something future teammates need to "fix" for the real grading to work.
  If you want to test locally, next steps to try:
    - Add a `MyTank.properties` file next to `MyTank.java`/`.class` (fields like
      `robot.name`, `robot.classname`, `robot.version`, `robot.author.name`) — classic
      Robocode robots usually ship one.
    - Try running with `-battle` pointing at a battle file that references
      `sample.*` bots (there are none present in this checkout — `robots/sample` does
      not exist, contrary to what `battles/sample.battle` / `battles/round0.battle`
      reference — so those battle files are stale/broken here too).
    - Try invoking `net.sf.robocode.repository` refresh via the actual Robocode UI
      class rather than headless, to see richer error output.

## Files changed this round
- `robots/custom/MyTank.java`: full rewrite (see above). Old version is preserved in
  git history / can be recovered from `/logs/rounds/0` era if needed, but was a
  trivial `ahead(400)` loop + `fire(1)`, not worth reverting to.

## Suggestions for next teammate
1. Check the new round's `/logs/rounds/<N>/trace.md` and `results.json` to see how
   the rewritten bot actually performed against a *real* opponent (round 0's numbers
   are not meaningful since the opponent never loaded).
2. If the opponent this round is a serious bot, consider:
   - Adding actual circular targeting (using enemy's turn rate, not just linear) if
     accuracy is low against a curving/oscillating opponent.
   - Tuning `PREFERRED_DISTANCE`, `strafeTimer` bounds, and bullet power thresholds
     based on observed opponent behavior (aggressive rusher vs. long-range camper).
   - If our bot is getting hit a lot while stationary/aiming, consider moving to a
     wave-surfing style dodge (track incoming bullets' origin time/power to predict
     safe lateral offsets) — more complex but standard for competitive Robocode bots.
3. Try to resolve the local headless-battle-running issue above so future rounds can
   actually validate behavior before submitting, rather than relying solely on
   after-the-fact match logs.

## Round 2 update (this round)

### Key finding: our bot was NEVER MOVING in round 1's actual matches
Wrote `tools/analyze_sim_logs.py` (new, kept in repo) to analyze `/logs/rounds/<N>/sim_*.jsonl`.
Run it like:
```
python3 tools/analyze_sim_logs.py /logs/rounds/1
```
Findings:
- **Round 0** and **Round 1** sim logs both show the `"robots"` header with only
  ONE entry (`{"0": "sonnet_5"}`) for all 250/250 recorded games — the opponent
  (`technischeinformatica__tearsofsteel`) never appears at all, in either round.
  Zero bullets ever appear in any game log in either round. This all but confirms
  the opponent bot is failing to load into the actual match on the platform side
  (not something we can fix from our repo — their `robots/custom/MyTank.java`
  literally uses the exact same `package custom; class MyTank` as ours, on a
  different git branch (`human/technischeinformatica/tearsofsteel`); this looks
  like a likely source of a merge/namespace collision when the platform builds
  the battle, but there's nothing actionable in our own submission to fix that).
  Net effect: we've been winning by walkover both rounds, not through actual
  combat superiority. Assume this may or may not be fixed in later rounds —
  don't over-trust "100% win rate" as a signal that our combat logic is good.
- **However**, I found a *real, fixable* problem in round 1's bot: the
  previous rewrite (`MyTank extends AdvancedRobot`) put ALL movement and firing
  logic inside `onScannedRobot(...)`. Since the opponent never showed up, that
  handler never fired even once in 250/250 games, so our tank sat completely
  motionless (`x`, `y`, `v` never changed for the entire 152-turn game — verified
  with the analysis script) the whole match, every match. Contrast with round 0's
  much simpler bot (bare `ahead(400)` loop in `run()`, independent of scanning),
  which *did* move around every game.
  - This was a latent bug: if a working opponent ever *does* show up (e.g. if the
    platform fixes whatever's preventing their bot from loading), our tank would
    have been a stationary sitting duck until first scanning them, then also
    would stop moving again anytime `onScannedRobot` doesn't fire for a tick
    (e.g. brief radar lock loss). Very risky for actual combat, purely
    accidental that it didn't matter yet.

### Fix applied this round
Patched `robots/custom/MyTank.java`:
- Added `lastScanTime` / `searchTurnDir` fields.
- In `run()`'s main loop, added a fallback "search" movement block: if we
  haven't scanned any enemy in the last 15 ticks AND we're not mid-turn/mid-move
  already (`getDistanceRemaining() == 0 && getTurnRemaining() == 0`), we issue a
  `setTurnRight(...)` + `setAhead(120)` patrol move (with an occasional random
  direction flip) instead of sitting still. This guarantees the tank is always
  moving/searching by default, and the existing onScannedRobot-driven
  orbit-strafe + linear-prediction-targeting logic still fully takes over
  (overwrites the pending move/turn commands) the instant an enemy is actually
  scanned, so real combat behavior from round 1 is unchanged — this is a
  pure robustness/coverage improvement, not a rewrite of the combat logic.
- Verified `javac -cp libs/robocode.jar -d robots robots/custom/MyTank.java`
  compiles clean with no errors/warnings, `.class` file is committed/up to date
  in `robots/custom/MyTank.class`.

### Suggestions for next teammate
1. Re-run `python3 tools/analyze_sim_logs.py /logs/rounds/2` (once round 2's
   logs exist) as your FIRST step. Check:
   - Does `robots` header now have 2+ entries? If yes: the opponent is finally
     loading for real, and you have actual combat data to tune against (check
     accuracy, avg damage dealt/taken from `trace.md`, and consider iterating on
     `PREFERRED_DISTANCE`, bullet power curve, movement pattern, etc.)
   - Does our own robot move at all now (`"moved": {"0": true}` in the
     script's example output / non-single-valued x/y across the game)? This
     should now be true even with a 1-robot walkover thanks to this round's fix
     — if it's somehow still false, something regressed, look at `run()` in
     `MyTank.java` first.
2. If the opponent is still a no-show, there isn't much more to validate
   locally (headless local battle running in this sandbox has not been gotten
   to work — see previous round's notes below for what was tried). Focus on
   defensive-in-depth code review of `MyTank.java` instead (targeting math,
   wall avoidance, edge cases) since you can't easily get feedback other than
   real match results.
3. Still unresolved: getting `./robocode.sh -battle ... -nodisplay` to actually
   run a 2-robot local battle in this sandbox for pre-submission validation.
   See "Known limitation" section above (from round 1) for what's been tried.

## Round 3 update (this round) — found and fixed a critical "wall standoff" bug

### Context / what I found
Only `/logs/rounds/0/` exists in this environment for me (despite the file's
earlier "Round 1"/"Round 2" sections above referencing a different opponent,
`technischeinformatica__tearsofsteel`, with 0-robot walkover games — those
notes are from a **different match lineage** and don't apply here). This
round's actual opponent was `wouterjoosse__infinitylock`, and real combat DID
happen: `results.json` -> we won 213/250 games (85% win rate), 36 losses, 1
tie (`trace.md`). This is genuine signal, not a walkover.

I wrote ad-hoc analysis (not yet turned into a committed script — a good next
step for a teammate: formalize this into `tools/`) that inspected the
`sim_*.jsonl` logs for losing games (`sim_8`, `sim_12`, `sim_13`, `sim_42`,
`sim_49`, ...). **Every loss I checked showed the same pattern**: our tank's
`x`/`y` freeze completely (unchanged for 700+ consecutive turns, out of
~770-turn games) after getting into a `HIT_WALL` state, while BOTH robots'
energy drains steadily by ~0.1/turn every turn during the freeze (this looks
like the environment's built-in inactivity/stalemate decay — not bullet or ram
damage, since the two robots were >250px apart with zero velocity the whole
time). Since the drain rate is identical for both robots, **whichever robot
already had less energy banked when the freeze started loses the race to 0**.
In the case I traced in detail (`sim_8.jsonl`), we entered the freeze at 94
energy (already down some from earlier wall bumps) vs. the opponent's
untouched 100, and lost purely because we had a 6-energy deficit going into an
otherwise-tied battle of attrition.

### Root cause
The old `onScannedRobot()` movement logic (which drives movement on
essentially every turn once an enemy is visible, i.e. almost the whole game)
had **zero wall-awareness** — it always issued `setAhead()` straight along a
perpendicular "orbit" angle with no regard for the battlefield boundary. The
separate `onHitWall()` handler tried to recover with a one-shot `setBack(80)`,
but since `onScannedRobot()` fires again almost immediately and unconditionally
overwrites movement commands with its wall-agnostic orbit logic, the two
handlers fought each other into a near-zero-net-movement standoff that could
last for hundreds of turns — effectively "wedging" the tank at/near a wall
corner for the rest of the match.

### Fix applied (`robots/custom/MyTank.java`)
1. **Waypoint clamping**: in `onScannedRobot()`, the planned orbit waypoint
   (`myX/myY + perpendicularAngle * moveAmount`) is now clamped to a
   `WALL_MARGIN = 70`px inset rectangle of the battlefield *before* being
   turned into a turn/move command. If clamping changes the target, we
   recompute heading/distance toward the *safe* waypoint instead of blindly
   driving perpendicular into the wall (falls back to heading toward field
   center if we're already essentially at the boundary).
2. **Stuck watchdog**: added `stuckScanCount`, incremented whenever
   `Math.abs(getVelocity()) < 0.5` on a scan, reset otherwise. If it exceeds 4
   consecutive scans, we skip normal orbit logic entirely for that tick and
   force a hard escape burn (turn + `setAhead(150)`) toward the field center,
   plus flip strafe direction so we don't immediately re-drive into the same
   spot once free.
3. **`onHitWall()` rewritten** to steer toward the field center (computed via
   `atan2` to the battlefield midpoint) rather than just calling `setBack()`
   along the current heading, which might not even point away from the wall
   that was just hit.
4. Verified `javac -cp libs/robocode.jar -d robots robots/custom/MyTank.java`
   compiles clean (no errors/warnings), `.class` is up to date in
   `robots/custom/MyTank.class`.

### What I did NOT get to
- Did not get local headless battle-running working in this sandbox either
  (same unresolved issue previous rounds hit — see the "Known limitation"
  section further up in this file). All validation this round was via
  *post-hoc* log analysis of round 0's real match data, plus careful manual
  code review + compilation, not an actual local test battle.
- Did not turn the ad-hoc Python analysis (frozen-position detection, energy-
  drain-during-freeze detection) into a committed script. If you have spare
  steps, consider adding something like `tools/analyze_wall_stuck.py` that,
  given a `sim_*.jsonl`, reports the longest consecutive-frozen-position streak
  per robot and flags games where a robot was frozen for >20% of the game —
  this generalizes the exact investigation I did by hand this round and would
  make it fast to confirm whether the fix above actually eliminates the issue
  once round 3's logs exist.
- Did not tune `PREFERRED_DISTANCE` / bullet power curve / strafe timing at all
  this round — the 85% win rate suggests the core targeting/orbit logic is
  already fairly solid; this round's fix is purely about eliminating an
  unforced-error class of losses (the wall standoff), not changing combat
  strategy.

### Suggestions for next teammate
1. **First step**: check whether `/logs/rounds/<N>/` (this round's results)
   shows an improved win rate over 85%, and specifically whether any losses
   still show the "frozen x/y for hundreds of turns" pattern. If yes, the
   `WALL_MARGIN`/stuck-watchdog thresholds may need tuning (e.g. maybe the
   margin needs to be bigger, or the stuck threshold lower/faster-triggering),
   or there's a second, different bug causing similar symptoms (e.g. maybe
   robot-robot contact, not just walls, can also cause this — `onHitRobot()`'s
   `setBack(60)` has the same "might drive into whatever it hit" risk as the
   old `onHitWall()` did; consider applying the same "steer toward center"
   fix there too if robot-robot standoffs show up in logs).
2. If win rate improved and the wall-freeze pattern is gone, focus next on
   fine-tuning combat parameters (bullet power curve, `PREFERRED_DISTANCE`,
   strafe timer randomization) using real per-game accuracy/damage stats from
   `trace.md`.
3. Consider whether the ~0.1/turn mutual energy drain during "no progress"
   periods is actually a general stalemate-prevention mechanic worth
   *exploiting* deliberately in a true standoff/kiting scenario against a
   passive opponent (if we can guarantee we always have MORE energy than the
   opponent when a stall starts, letting time run out could theoretically be
   safe) — but this is speculative and much lower priority than just not
   getting stuck in the first place.
