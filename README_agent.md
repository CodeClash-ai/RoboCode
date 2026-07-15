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

## Round 4 update (this round) — found and fixed the "radar freeze" bug behind both round-1 losses

### Context
This round's opponent (per `/logs/rounds/1/`, which is the most recent completed
round available to me) was `wouterjoosse__infinitylock` again. `results.json` /
`trace.md` show a strong 96% win rate (241/250), only 2 losses
(`sim_1.jsonl`, `sim_41.jsonl`) + 1 draw. Real combat, not a walkover
(`python3 tools/analyze_sim_logs.py /logs/rounds/1` confirms 2+ robots and
bullets present in all 250 games).

### What I found investigating the 2 losses
Both losing games (`sim_1`, `sim_41`) show the *exact same* pattern, distinct
from the round-3 "wall standoff" bug that was already fixed:
- The opponent (`wouterjoosse__infinitylock`) is a **stationary sentry bot**:
  `x`/`y`/`v` never change for the *entire* game in every game I checked (it
  never moves and, in these two losses, never fires a single bullet either —
  `o=0` never appears in the bullet log at all). It should be an easy kill.
- In both losses, our own radar heading (`rh`) and gun heading (`gh`) froze
  completely partway through the game (confirmed by dumping robot 1's `rh`/`gh`
  every tick — e.g. in `sim_1.jsonl`, frozen at `rh=0.511` from t=520 onward, in
  `sim_41.jsonl` frozen at `rh=4.233` from t=202 onward), for the *rest of the
  match* (600-900+ more turns), even though our tank's `x`/`y` kept moving the
  whole time (the movement/search logic in `run()` is independent of scanning,
  so that part kept working).
- Once the radar stops sweeping, `onScannedRobot()` never fires again, so we
  never re-aim or fire again either. Both games then just coast for hundreds of
  turns with **zero bullets landing on anyone**, hitting Robocode's built-in
  inactivity-decay rule (~0.1 energy/turn drain on *both* robots once no hit has
  landed for a while). Since we'd already spent some energy on earlier bullets
  (bullet firing costs energy up front, hit or miss) while the sentry bot spent
  none (it never fires), we were already down some energy heading into the
  decay race, and eventually hit 0 first and died — purely due to the radar
  freeze, not because the opponent ever actually outplayed us.

### Root cause
`run()` called `setTurnRadarRight(Double.POSITIVE_INFINITY)` exactly **once**,
before the main loop, intending to keep the radar spinning forever so
`onScannedRobot()` fires regularly. But `onScannedRobot()` unconditionally
overwrites the radar command every time it runs with a small precise
"lock-on" turn (`setTurnRadarRightRadians(radarTurn * 1.5)`). If that
finite lock-on turn ever completes on a tick where the enemy is no longer
inside the radar's arc (e.g. our own body turned away doing orbit/search
movement, or the lock angle happened to be ~0), **nothing ever re-issues a
fresh infinite sweep** — the one-time call before the loop is long gone. The
radar then just sits frozen at whatever heading it stopped at, forever, since
nothing else in the codebase ever touches it again for the rest of the match.
This is a latent bug that can strike at any random point in *any* match,
independent of the round-3 wall-standoff bug (already fixed) — it just hadn't
shown up as a *loss* until this opponent's very low aggression (never
firing/moving) made attrition-by-decay the deciding factor instead of being
masked by combat damage swinging things one way or the other quickly.

### Fix applied (`robots/custom/MyTank.java`, in `run()`'s main loop)
Added, at the top of every loop iteration (before the existing movement
fallback and `execute()`):
```java
if (getRadarTurnRemaining() == 0) {
    setTurnRadarRight(Double.POSITIVE_INFINITY);
}
```
This re-issues the infinite spin *every single tick* the radar has no pending
turn left (i.e. any earlier command, ours or a stale one, has fully played
out) — a pure safety net. While we're actively tracking a target,
`onScannedRobot()`'s precise lock-on command fires afterward (during event
processing) and immediately overrides this for that tick, so real tracking
behavior from previous rounds is unchanged. But now, the instant the radar
would otherwise have frozen (lock-on turn completed, no scan to redirect it),
the very next loop iteration detects `getRadarTurnRemaining() == 0` and
restarts the sweep, guaranteeing we reacquire the enemy within at most one
full radar rotation instead of potentially never again.
Verified `javac -cp libs/robocode.jar -d robots robots/custom/MyTank.java`
compiles clean (no errors/warnings); `.class` is up to date in
`robots/custom/MyTank.class`.

### What I did NOT get to
- Did not get local headless battle-running working in this sandbox (same
  long-standing unresolved issue noted in every previous round's section
  above). All validation this round was via post-hoc analysis of round 1's
  real match logs (`tools/analyze_sim_logs.py`, plus ad-hoc per-tick dumps of
  `rh`/`gh`/`x`/`y` for the two losing games — the ad-hoc snippets aren't saved
  as a script; a good next step would be to generalize them into something
  like `tools/analyze_radar_freeze.py` that flags any game where a robot's
  `rh` stays byte-for-byte identical for more than, say, 100 consecutive
  ticks while the match is still ongoing — directly analogous to the existing
  wall-freeze detection idea from round 3's notes, but for radar instead of
  position).
- Did not touch movement/targeting-math/bullet-power tuning at all this round
  — the 96% win rate suggests the core strategy is sound; this round's fix is
  purely about eliminating a second unforced-error class of losses (radar
  freeze), analogous in spirit to round 3's wall-standoff fix.

### Suggestions for next teammate
1. **First step**: once this round's `/logs/rounds/<N>/` exists, re-run
   `python3 tools/analyze_sim_logs.py /logs/rounds/<N>` and check `trace.md`'s
   win rate. If it's now 98-100% (i.e. both prior loss patterns eliminated),
   the radar/wall fixes are validated — shift focus entirely to fine-tuning
   (bullet power curve, `PREFERRED_DISTANCE`, strafe timer) using per-game
   accuracy/damage stats.
2. If any losses remain, dump `rh`/`gh` and `x`/`y` per-tick for robot index 1
   (us) across the whole game (see the ad-hoc python snippets in this round's
   git history / shell scrollback if still needed as a template) and check for
   *either* a frozen `rh` (radar) *or* a frozen `x`/`y` (movement/wall) lasting
   >100 ticks — those are the two known failure classes so far. If you find a
   THIRD distinct freeze pattern (e.g. gun heading frozen while radar keeps
   spinning fine), that would indicate a new, not-yet-found bug in the gun
   turn logic specifically worth isolating.
3. Consider writing the general freeze-detector script mentioned above
   (`tools/analyze_radar_freeze.py` or extend `analyze_sim_logs.py` with a
   `--check-freezes` flag) so this class of bug is caught automatically from
   logs instead of requiring manual per-tick dumps each time.

## Round 5 update (this round) — confirmed real 100% win rate + added circular-motion gun prediction

### Context
Only `/logs/rounds/0/` exists in this environment for me (this appears to be a
fresh round-numbering lineage vs. the "Round 1-4" sections above, which were
from a different match history/opponent). This round's actual opponent per
`trace.md` is `robo_code__sittingduck`. Confirmed via
`python3 tools/analyze_sim_logs.py /logs/rounds/0`: **250/250 games have 2
real robots present, bullets fired, and movement** — this is genuine combat,
not a walkover. Result: **100% win rate (250/250)**, 94% bullet accuracy,
avg min energy 96 (i.e. we barely took damage). The opponent
(`robo_code__sittingduck`) is exactly what its name says: it never moves
(`avg speed 0.0`) and never fires (`avg shots 1.0`... actually shows minimal/no
real shots) — a stationary punching bag. So this 100% win rate, while real
combat, doesn't tell us much about how we'd do against an aggressive/skilled
opponent; it mostly confirms our targeting/movement code doesn't have any
fatal bugs left (no walls-stuck freeze, no radar freeze — see below).

### New tool: `tools/analyze_freezes.py`
Implemented the generalized freeze-detector previous rounds' notes asked for
(scanning wall-standoff / radar-freeze classes of bugs from `sim_*.jsonl`
logs). Usage:
```
python3 tools/analyze_freezes.py /logs/rounds/<N> --threshold 100
```
Flags any robot whose position (`x`,`y`) or radar heading (`rh`) stays
byte-identical for more than `--threshold` (default 100) consecutive ticks
while the game is still running. Ran it against round 0's logs: **all 500
findings were on the *opponent* robot** (`robo_code__sittingduck` — expected,
since it's a stationary sentry bot by design, not a bug), and **zero findings
on our own robot** (`sonnet_5`). This is good evidence the round-3
"wall-standoff" fix and round-4 "radar-freeze" fix (see notes further up this
file) are both still holding up and haven't regressed. If you inspect a future
round's logs and see findings mentioning our bot's name specifically, that's a
real regression worth digging into with the same tool.

To filter to just your own bot's findings, pipe through grep for your bot's
name/id, e.g. `... | grep "(sonnet_5)"`.

### Change made this round: circular-motion gun prediction
`robots/custom/MyTank.java`'s gun targeting previously only did **linear**
prediction (assumed the enemy keeps a constant heading/velocity for the whole
bullet flight time, iterated a few times to converge on flight time). This
works fine against a bot that goes straight, but is weak against any opponent
that curves/orbits (including, ironically, a bot doing the same kind of
perpendicular strafing our own movement logic does) since the aim point
converges to somewhere the target will have already turned away from.

Replaced it with **tick-by-tick simulated prediction that also incorporates
the enemy's estimated turn rate**:
- Track `prevEnemyHeading` / `prevEnemyScanTime` across scans of the enemy.
- On each new scan, compute `enemyHeadingRate` = (change in enemy heading) /
  (ticks since last scan of them) — but only trust this if the gap since the
  last scan was small (`<= 3` ticks); if we lost lock for a while and just
  reacquired, assume 0 (straight-line fallback) rather than risk basing a turn
  rate estimate on a huge/stale time gap.
- Simulate the enemy's future position forward **tick-by-tick** (not a
  closed-form solution) for up to 60 simulated ticks, applying
  `enemyHeadingRate` each tick to `simHeading` before advancing position by
  `enemyVelocity` along that heading, and clamping to stay inside the
  battlefield (a real enemy can't walk through walls either). Stop as soon as
  the bullet's travel time to the *current* predicted point is <= the
  simulated time elapsed so far (i.e. the bullet would have already arrived).
- This degrades gracefully to the old linear-prediction behavior whenever
  `enemyHeadingRate ~= 0` (enemy going straight), so no regression against
  straight-line movers/sentries — round 0's 100%/94%-accuracy result was
  produced *after* this change (I made the change and verified compilation
  before this round's match ran... actually to be precise: I don't have
  access to know exactly when in the round the match played vs. when I edited
  — treat round 0's 100% result as validation of the *pre-change* code, and
  treat this change as *not yet validated by a real match* at the time of
  writing this note. See "Suggestions for next teammate" below.)
- Verified `javac -cp libs/robocode.jar -d robots robots/custom/MyTank.java`
  compiles clean, no errors/warnings; `.class` is up to date in
  `robots/custom/MyTank.class`.
- Old pre-change version of `MyTank.java` preserved at
  `archive/round1_backups/MyTank.java.before_circular_targeting` in case this
  change needs to be reverted/compared.

### What I did NOT get to
- Did NOT get to validate the new circular-prediction gun logic against a real
  match (no local headless-battle-running available in this sandbox, per every
  previous round's notes — still unresolved). This is a real, un-battle-tested
  code change, unlike most of the earlier rounds' bugfixes which were
  validated after-the-fact against logs showing the *specific bug pattern*
  being gone. There is some risk the tick-by-tick simulation has an edge case
  (e.g. `bulletSpeed` could theoretically be 0 if `bulletPower` were ever 20/3
  — but `bulletPowerForDistance()` only returns 1.0-3.0, so `bulletSpeed` is
  always in `[11, 17]`, no div-by-zero risk there). Recommend double-checking
  behavior/accuracy stats in the *next* round's `trace.md` closely.
- Did not do anything about bullet-power tuning, `PREFERRED_DISTANCE`, or
  movement/strafe timing this round — those were already producing a 94%
  accuracy / 96-min-energy result against this (admittedly passive) opponent,
  didn't seem like the priority given the opponent's total passivity means we
  can't tell if those specific parameters are well-tuned or not anyway.

### Suggestions for next teammate
1. **First step**: check whether `/logs/rounds/<N>/trace.md` this round still
   shows ~100% win rate and similar-or-better accuracy vs whatever opponent
   you're facing. If accuracy *drops* noticeably vs round 0's 94% baseline
   against a similar (mostly-straight-line or stationary) opponent, suspect
   the new circular-prediction code (in `onScannedRobot()`'s targeting block)
   introduced a regression — compare against
   `archive/round1_backups/MyTank.java.before_circular_targeting` and consider
   reverting if so.
2. Run `python3 tools/analyze_freezes.py /logs/rounds/<N> --threshold 100 |
   grep "(sonnet_5)"` (substitute your bot's actual name/id if different) as a
   quick regression check for the wall/radar freeze bug classes — should
   print nothing if healthy.
3. If you get a genuinely aggressive/moving opponent next round (unlike this
   sitting-duck one), that's the first real chance to see whether the new
   circular-targeting code actually improves hit rate against a curving
   target vs. the old pure-linear version — worth specifically checking
   accuracy stats in that case.
4. Local headless battle running in this sandbox is still unresolved (every
   round's notes mention trying and failing) — if you have spare steps and
   want to finally crack it, that would make all future rounds much easier to
   validate confidently instead of relying on post-hoc log analysis of
   real matches only.
