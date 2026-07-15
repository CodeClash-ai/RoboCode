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

## Round 6 update (this round) — confirmed bot is healthy, made progress on the long-standing local-battle-testing issue

### Context
Only `/logs/rounds/0/` and `/logs/rounds/1/` exist in this environment for me. Both
are real combat (not walkovers) against `robo_code__sittingduck` (per git log,
this is "Rung 3/115"). Results: **100% win rate both rounds** (250/250 and
250/250), ~94% bullet accuracy, avg min energy 96, no losses at all.
Ran `python3 tools/analyze_freezes.py /logs/rounds/1 --threshold 100 | grep -i
sonnet` -> **zero matches**, confirming the wall-freeze (round 3 fix) and
radar-freeze (round 4 fix) bugs documented earlier in this file are NOT
regressing — all 500 freeze findings in round 1's logs are on the opponent
(`robo_code__sittingduck`), which is expected since it's a stationary sentry
bot by design.

Given the current opponent is a fully passive, non-moving, non-firing
sentry, and we're already winning every single game with high accuracy and
minimal damage taken, there isn't much combat-parameter tuning that would
show up as a measurable improvement against *this specific* opponent — our
bottleneck isn't targeting/movement quality, it's just closing out the kill.
I chose NOT to make speculative combat-logic changes this round (e.g.
hard-coding max bullet power always) since:
1. It's unclear whether the *same* opponent will be faced in subsequent
   rounds/rungs (git history shows opponents changing roughly every 2 rounds:
   `technischeinformatica__tearsofsteel` -> `wouterjoosse__infinitylock` ->
   `robo_code__sittingduck`), so over-fitting to a passive sentry bot (e.g.
   removing distance-based bullet power conservation) could hurt us against
   a real, energy-punishing opponent in a future rung.
2. The existing distance-scaled bullet power logic
   (`bulletPowerForDistance()`) is a generally sound, opponent-agnostic
   default (more power close range where hit-chance is high, less far away
   to conserve energy) and isn't broken — no evidence in the logs of it
   costing us games.

### Real progress: found the actual root cause of the long-standing "local headless battle won't run" issue (partially)
Every previous round's notes (rounds 1-5 above) recorded failing to get
`./robocode.sh -battle ... -nodisplay` working locally for pre-submission
validation. I made concrete progress on this:

1. **First blocker (found + fixed): missing JVM `--add-opens` flags.**
   Calling `java -cp "libs/*" robocode.Robocode -battle ...` directly (as
   suggested by earlier rounds' repro steps) throws
   `java.lang.ExceptionInInitializerError` /
   `InaccessibleObjectException: Unable to make field ... accessible` from
   `net.sf.robocode.io.URLJarCollector`, because `robocode.sh` normally adds
   several `--add-opens=...=ALL-UNNAMED` flags before invoking `java` (see
   `robocode.sh`'s `java \ -cp "libs/*" ... "--add-opens=...`) that get lost
   if you invoke `java` directly without them, and separately `robocode.sh`
   itself has the `cd "${0%/*}"` quirk noted in round 1's writeup. Fix: pass
   the same 4 `--add-opens` flags manually:
   ```
   java -cp "libs/*" -Xmx512M \
     --add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED \
     --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens=java.desktop/javax.swing.text=ALL-UNNAMED \
     --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
     robocode.Robocode -battle battles/<yourbattle>.battle -nodisplay -nosound -results /tmp/r.txt
   ```
   This gets you past the crash and into an actual (empty, see below) battle
   run with clean log output.

2. **Second blocker (found, NOT fixed): stale `robots/robot.database`.**
   The checked-in `robots/robot.database` is a serialized Java object cache
   built on the original developer's machine
   (`/Users/johnbyang/Desktop/games/robocode/robots/...` — verified via
   `strings robots/robot.database | head`), which doesn't exist in this
   sandbox. I deleted it and reran; Robocode regenerated a fresh
   `robot.database` referencing the *correct* sandbox paths (verified via
   `strings robots/robot.database | grep MyTank` showing
   `/workspace/robots/custom/MyTank.class` etc. correctly), so the repository
   scanner CAN find and cache our robots when starting fresh.

3. **Third blocker (found, NOT fixed — this is why local battles still don't
   work): `loadSelectedRobots()` still logs `Can't find 'custom.MyTank'` even
   with a freshly-regenerated, correct `robot.database`, and the resulting
   battle silently runs with ZERO robots (empty `results.txt`, rounds
   "initialize"/"clean up" with no scores).** Looking at the stack trace
   ordering: `BattleManager.startNewBattle -> RepositoryManager
   .loadSelectedRobots -> checkDbExists -> reload`. This means
   `loadSelectedRobots` is called and fails to resolve `custom.MyTank`
   *before* `checkDbExists`/`reload` has re-scanned the repository into
   memory for this process — i.e. `loadSelectedRobots` appears to look up
   robots in an in-memory list that's empty at that point in the same call,
   not re-querying after the reload it itself triggers. This reproduces
   identically on a second, subsequent run even after the database file is
   confirmed correct on disk — so it's not a "run it twice" fix. I did NOT
   find a fix for this within this round's remaining time. I tried:
   creating a `custom/MyTank.properties` sidecar file (classname/name/author
   fields) as earlier rounds speculated might be needed — did not help by
   itself.
   - **This confirms (again) that the real grading harness must be doing
     something different from a plain `robocode.sh -battle` invocation**
     (e.g. maybe it forces two full separate process invocations — one to
     build the repo cache, one to battle — or calls
     `RepositoryManager.reload()`/refresh explicitly before selecting
     robots, or uses a totally different entry point/API rather than the
     `-battle` CLI flag). Since real matches against real opponents (rounds
     0 and 1, both against `robo_code__sittingduck`, and previous rounds
     against other opponents) have consistently loaded and run our
     `custom.MyTank` successfully for many rounds now, this is purely a
     local-sandbox testing convenience issue, not something affecting actual
     scored matches.
   - I did NOT commit any test scaffolding for this (a throwaway
     `robots/custom2/MyTank2.java` dummy opponent + `battles/test_self.battle`
     I created to test with were deleted again before finishing this round,
     specifically to avoid any risk of the extra files confusing the *real*
     grading harness's robot discovery) — if you want to continue this
     investigation, recreate a simple second robot under a new package (e.g.
     `custom2`) plus a `.battle` file listing both `selectedRobots`, and try
     digging into `net.sf.robocode.repository.RepositoryManager` (class
     files are inside `libs/robocode.repository-1.10.0.jar` — extract/
     decompile if needed) to understand why `loadSelectedRobots` doesn't see
     the freshly reloaded repository contents in the same call.

### What I did NOT get to
- Did not make any changes to `MyTank.java`'s combat logic this round — logs
  show it's healthy (100% win rate, no freeze regressions) and I didn't want
  to risk a speculative, unvalidated change (local battle-testing still isn't
  fully working, see above) against an opponent this passive where I can't
  actually tell if a tweak like "always max bullet power" helps or hurts
  without real match feedback anyway.
- Did not finish resolving the local battle-runner (see blocker 3 above) —
  this is the closest anyone has gotten in 5+ rounds of trying (JVM flags
  fixed, stale-cache issue identified and fixed for that one file, only the
  `loadSelectedRobots`-ordering issue remains), so a future teammate with a
  bit more step budget has a much narrower, well-documented problem to dig
  into now instead of starting from scratch.

### Suggestions for next teammate
1. Check `/logs/rounds/<N>/trace.md` for this round's actual opponent/result
   first, as always. If it's still `robo_code__sittingduck` and still 100%,
   this rung is probably about to change opponents (per git history, ~2
   rounds per rung) — the *next* opponent may be much more aggressive, so
   re-run `tools/analyze_freezes.py` and check accuracy/avg-min-energy
   numbers closely for the first time against a real threat.
2. If you want to finish the local-battle-runner fix: reproduce blocker 3
   above (delete `robots/robot.database`, use the full `--add-opens` java
   command documented above with a 2-robot `.battle` file) and dig into
   `RepositoryManager.loadSelectedRobots`/`checkDbExists`/`reload` (decompile
   from `libs/robocode.repository-1.10.0.jar` if source isn't already
   available under e.g. a `src/` directory — I didn't check for that this
   round, worth a quick `find / -iname RepositoryManager.java` first).
3. Otherwise, focus stays the same as previous rounds: watch for a real
   aggressive opponent to finally validate the circular-motion gun
   prediction (round 5's change) and current bullet-power/movement tuning
   against something that fights back.

## Round 7 update (this round) — bullet power increase + onHitRobot/search wall-awareness fixes

### Context
Only `/logs/rounds/0/` exists in this environment for me. Per `trace.md`, this
round's real opponent is `it_economics__ite_bomax` (a new rung — different from
`robo_code__sittingduck` seen in rounds 5-6's notes above). Confirmed via
`python3 tools/analyze_sim_logs.py /logs/rounds/0`: 250/250 games have 2 real
robots, bullets, and movement (real combat). Result: **100% win rate (250/250)**,
69% accuracy, avg speed 6.0, avg walls/game 2.5, avg rams/game 1.1, avg min
energy 93. The opponent is weak (0% win rate, 4% accuracy, avg speed 0.8 — barely
moves and almost never hits us) but not a total no-op sentry like
`robo_code__sittingduck` was (it does fire occasionally, just very inaccurately).

Ran `python3 tools/analyze_freezes.py /logs/rounds/0 --threshold 100 | grep -v
it_economics` -> **zero matches on our own bot (`sonnet_5`)**, confirming the
round-3 wall-standoff and round-4 radar-freeze fixes are still holding (all 500
freeze findings are on the opponent, and in this case they correspond to it
being dead/destroyed mid-game and staying frozen for the remainder — expected,
not a bug, since `it_economics__ite_bomax`'s avg death turn is ~204 out of
avg-355-turn games).

### Changes made this round (`robots/custom/MyTank.java`)
Since we're already winning every game comfortably against a weak opponent,
focused on squeezing more *score* (damage dealt / efficiency) out of games we
were already winning, plus two small defensive-in-depth cleanups, rather than
touching the core targeting/movement algorithms (which look healthy):

1. **`bulletPowerForDistance()` increased across all bands**
   (0-150: unchanged 3.0; 150-350: 2.2->2.6; 350-550: 1.5->2.0; 550+: 1.0->1.3).
   Rationale: this rung's opponent barely moves (avg speed 0.8) and has very low
   accuracy against us (4%), so slightly slower bullets (higher power => lower
   `bulletSpeed = 20 - 3*power`) shouldn't meaningfully hurt our hit rate against
   a near-stationary target, while `4*power + 2*max(0,power-1)` damage-per-hit
   scaling means more power per landed hit converts directly into more damage
   dealt (and, presumably, more score) per game. **NOT yet validated by a real
   match** (no local battle-runner available in this sandbox — see many earlier
   rounds' notes on this unresolved issue) — if next round's `trace.md` shows
   accuracy dropping *sharply* (not just a point or two of normal variance) vs
   this round's 69% baseline, suspect this change and consider reverting bands
   back toward the old values (old version preserved at
   `archive/round1_backups/MyTank.java.before_round7_tuning`).
2. **`onHitRobot()` now steers toward field center before backing away**
   (previously just `setBack(60)` along current heading with no directional
   awareness at all) — mirrors the fix already applied to `onHitWall()` in an
   earlier round, for the same reason: blindly backing up along whatever
   heading we happened to be facing when we bumped something could just as
   easily re-drive us into the same wall/robot corner repeatedly instead of
   actually escaping. Round-0 logs show avg 1.1 rams/game as a plausible
   (if minor, since we still won every game) source of avoidable contact
   damage.
3. **Fallback "search" movement (in `run()`'s main loop, used when we haven't
   scanned an enemy in 15+ ticks) is now wall-aware.** Previously this was the
   *one* remaining movement path with zero wall-margin logic (the
   `onScannedRobot()`-driven orbit movement got wall-clamping back in round 3,
   but the blind patrol/search fallback never did). Added a check: if current
   position is within 100px of any edge while in search mode, steer toward
   field center instead of the plain random-turn patrol. Low-impact fix (this
   fallback path rarely triggers once an enemy has been scanned at all,
   per round 2's notes) but closes a previously-unaddressed gap.

Verified `javac -cp libs/robocode.jar -d robots robots/custom/MyTank.java`
compiles cleanly (no errors/warnings); `.class` is up to date. Full diff
against the pre-round-7 version is preserved via
`archive/round1_backups/MyTank.java.before_round7_tuning` (`diff` it against
the current file to see exactly what changed this round if you need to revert
piecemeal).

### What I did NOT get to
- Did not touch `PREFERRED_DISTANCE`, strafe timing, radar lock, or the
  circular-motion gun prediction math (round 5's change) at all — no evidence
  in the logs that any of those are underperforming, and this rung's opponent
  is too passive to give strong signal either way on movement/dodging quality.
- Still did not resolve local headless-battle-running in this sandbox (every
  round back to round 1 has tried and failed/partially-progressed on this —
  see round 6's section above for the most detailed writeup of exactly where
  it currently breaks, `RepositoryManager.loadSelectedRobots` not seeing a
  freshly-reloaded repository within the same call). All validation this round
  was static (code review + compilation), same limitation as every prior
  round's bugfix-only changes, but this round's *bullet power* change in
  particular is a genuine behavior change to already-working combat code
  (unlike e.g. round 3/4's freeze fixes, which only mattered in previously-
  broken edge cases) — so it carries slightly more risk of an unseen
  regression than most previous rounds' changes. Flagged clearly above for
  the next teammate to double check against real results.

### Suggestions for next teammate
1. **First step**: check `/logs/rounds/<N>/trace.md` for this round's result.
   - If win rate stayed ~100% and accuracy is roughly steady (65-75%) or
     higher, with average damage-dealt/score noticeably higher than this
     round's baseline (38929 team score per `results.json` this round), the
     bullet-power increase is validated — consider pushing power even higher
     if there's still no accuracy/win-rate cost, or leave as-is if the score
     gain has plateaued.
   - If accuracy craters or (unlikely, given the opponent, but check anyway)
     a loss appears, revert `bulletPowerForDistance()` back to the values in
     `archive/round1_backups/MyTank.java.before_round7_tuning`.
2. Run `python3 tools/analyze_freezes.py /logs/rounds/<N> --threshold 100 |
   grep sonnet` (or your bot's actual name) as a standard regression check —
   should print nothing.
3. If/when the ladder rung changes to a genuinely aggressive, accurate
   opponent (this one and `robo_code__sittingduck` before it have both been
   quite weak/passive), that's the first real opportunity to see how the
   round-5 circular-motion gun prediction and current movement/strafe tuning
   hold up under real pressure — worth close attention to avg-min-energy and
   whether losses start appearing at all.
4. Local headless battle-runner: still unresolved, still the single most
   valuable thing a future teammate with steps to spare could fix, per every
   round's notes back to round 1. See round 6's section for the most specific
   known blocker (`RepositoryManager.loadSelectedRobots` ordering issue).

## Round 8 update (this round) — confirmed round 7 tuning validated, pushed bullet power/distance further

### Context
`/logs/rounds/0` and `/logs/rounds/1` both exist this round, both real combat
(confirmed via `tools/analyze_sim_logs.py`) against `it_economics__ite_bomax` —
same opponent as round 7's notes above, so round 1's logs are the *actual real
match result* of round 7's bullet-power-increase change (which was previously
unvalidated). Result: **100% win rate both rounds** (250/250 each), and
round 7's change is validated as a genuine improvement, not a regression:
- Round 0 (pre-round-7-change baseline, per its own trace.md): 69% accuracy,
  team score 38929.
- Round 1 (post-round-7-change, i.e. this same tuning played out for real):
  **70% accuracy** (slightly up, not down), team score **39892** (up ~2.5%).
  So increasing bullet power did NOT cost us accuracy or win rate against this
  opponent, and did increase score. Ran `tools/analyze_freezes.py` on round 1's
  logs too — all findings are on the opponent (expected, it dies mid-game and
  stays frozen), zero on our own bot, so the wall/radar freeze fixes are still
  holding with no regression.

### Change made this round
Since round 7's "push bullet power up" direction was validated by a real
match, pushed the same lever further, plus a matching movement tweak, still
targeting the same lever (more damage per hit against a nearly-stationary,
barely-accurate opponent) rather than touching movement/targeting math that
already looks healthy:
1. `bulletPowerForDistance()`: 150-350 band 2.6->2.9, 350-550 band 2.0->2.2,
   550+ band 1.3->1.5. (0-150 band stays at 3.0, the Robocode engine's hard
   max bullet power — can't push that one further.)
2. `PREFERRED_DISTANCE` reduced 300->220, so our orbit-strafing spends more of
   its time inside the 150-350 (2.9-power) band instead of hovering near its
   outer edge. Still leaves clear margin from the 150 threshold (don't want to
   cross into point-blank/ramming range unintentionally) and plenty of room
   from the wall-margin/stuck-watchdog logic.
Old version (round 7's values, pre-this-round) preserved at
`archive/round1_backups/MyTank.java.before_round8_tuning` for a quick diff/
revert if needed. Verified `javac -cp libs/robocode.jar -d robots
robots/custom/MyTank.java` compiles clean, `.class` up to date.

### What I did NOT get to
- As with round 7, this is a real behavior change to already-working combat
  code (not just a bugfix for a previously-broken edge case), so it carries
  more regression risk than a pure bugfix, and — same limitation as literally
  every previous round — I could NOT validate it locally (headless battle
  running in this sandbox remains unresolved; see round 6's section above for
  the most detailed writeup of exactly where that effort currently gets stuck,
  `RepositoryManager.loadSelectedRobots` not seeing a freshly-reloaded
  repository within the same call — nobody has fixed this in 7+ rounds now,
  might be worth a fresh, focused attempt if a future teammate has steps to
  spare and wants faster iteration than "wait a round and read the logs").
- Did not touch the circular-motion gun prediction (round 5), radar lock-on,
  wall-avoidance/stuck-watchdog (round 3), or `onHitRobot`/`onHitWall`/search
  wall-awareness (round 4/7) logic at all this round — all of that continues
  to look healthy in the logs (no freezes, 100% win rate, high survival) and
  didn't seem like the priority while this opponent is still so passive.

### Suggestions for next teammate
1. **First step, as always**: check the newest `/logs/rounds/<N>/trace.md`.
   - If accuracy stayed >=~68% and win rate stayed 100% with team score
     >=~39892 (this round's/round-1's baseline), this round's change is
     validated — consider whether it's worth pushing bullet power/distance
     even further, or whether returns have plateaued (e.g. if accuracy starts
     dropping because we're now landing in awkward ram-adjacent range too
     often, or getting hit more due to being closer).
   - If accuracy or win rate drops noticeably, or a loss appears, revert via
     `archive/round1_backups/MyTank.java.before_round8_tuning` (round 7
     values: bands 2.6/2.0/1.3, PREFERRED_DISTANCE 300).
2. Run `python3 tools/analyze_freezes.py /logs/rounds/<N> --threshold 100 |
   grep -v it_economics` (substitute the actual opponent name if it's changed)
   as a standard regression check for the wall/radar freeze bug classes —
   should print nothing if we're still healthy.
3. Watch for the ladder rung finally changing to a different, more aggressive
   opponent (per git history this rotates every ~2 rounds, and this is now the
   *second* round against `it_economics__ite_bomax`) — that would be the
   first real test of whether the round-5 circular-motion gun prediction and
   this round's closer-orbit-distance tuning hold up against something that
   actually punishes proximity (faster/more-accurate return fire, deliberate
   ramming, etc.), rather than another data point against a passive opponent.
4. Local headless battle-runner: still unresolved after 7+ rounds of attempts
   (see round 6's section for the most specific known blocker). Would be the
   single highest-leverage infra fix for future rounds if anyone wants to dig
   in with a larger step budget than a single round typically allows.

## Round 9 update (this round) — new opponent confirmed, added turn-rate smoothing to gun prediction

### Context
Only `/logs/rounds/0/` exists in this environment for me. Per `trace.md` /
`results.json`, this round's opponent is a **new** one, `trex22__deepthought`
(different from `it_economics__ite_bomax` seen in rounds 7-8's notes above).
Confirmed real combat via `python3 tools/analyze_sim_logs.py /logs/rounds/0`
(2 robots, bullets, movement present in all 250 games). Result: **100% win
rate (250/250)**, team score **43836 vs opponent's 210** (huge margin),
42% accuracy, avg speed 6.4, avg min energy 86, opponent avg death turn 306
(out of avg-457-turn games, so we typically finish it off well before the
match would time out). `tools/analyze_freezes.py --threshold 100 | grep -i
sonnet` -> **zero matches**, confirming the round-3 wall-standoff and round-4
radar-freeze fixes are still holding with no regression.

The opponent itself is much weaker than us (0% win rate, 2% accuracy, avg
shots only 1.9/game) but noticeably more *mobile* than the last two rungs'
opponents (`robo_code__sittingduck` / `it_economics__ite_bomax`, both nearly
stationary): avg speed 2.8, and inspecting `sim_0.jsonl` by hand showed its
heading (`bh`) changes by >0.05rad on 75 out of 502 ticks — i.e. it moves in
straight bursts up to top speed, then makes fairly frequent sharp turns
(looks like a segment-based/zigzag movement pattern, not smooth circular
orbiting). This is plausibly why our accuracy (42%) is noticeably lower than
the last two rungs (69-70% against nearly-stationary opponents) — it's just a
harder target to hit, not necessarily a bug. Even so, 42% accuracy plus a
100% win rate with a >200x score margin is still a very strong result; there
is no losing pattern to fix this round, just a possible efficiency
improvement.

### Change made this round: smoothed enemy turn-rate estimate for gun prediction
`onScannedRobot()`'s circular-motion gun prediction (added in round 5, see
that section above) estimates the enemy's turn rate as a *raw, single-scan*
`(heading_delta / scan_gap)` value and then simulates it forward
tick-by-tick assuming that rate holds constant for the whole predicted
bullet flight. Against a bot that makes occasional sharp single-tick
corrections while otherwise going straight (which `trex22__deepthought`'s
movement pattern, per the `bh` analysis above, looks like it might do), this
raw estimate is exactly the wrong assumption: extrapolating a one-tick turn
as if it were a sustained curve for the next 10-60 simulated ticks would aim
well off to the side of where the target actually ends up (it stops turning
almost immediately in reality).

Fix (`robots/custom/MyTank.java`, in the turn-rate-estimation block inside
`onScannedRobot()`):
1. Clamp the raw per-tick heading-delta estimate to +/-0.15 rad/tick before
   using it at all (a real robot's max turn rate tops out around 0.116
   rad/tick at v=0 and less at higher speed, so anything further beyond that
   from a single scan sample is almost certainly noise, e.g. from a
   momentarily large scan gap, not real sustained rotation).
2. Blend the clamped raw estimate with the *previous* smoothed estimate via
   a simple 50/50 low-pass filter (`enemyHeadingRate = 0.5*old + 0.5*new`)
   instead of overwriting it outright. This damps single-tick spikes (they
   only get half-weighted in, and then decay by half again each subsequent
   tick if not repeated) while a genuinely sustained turn (same-sign delta
   for several consecutive scans, e.g. a bot doing real circular
   orbiting) still converges to being tracked within just a few ticks.
   Degrades gracefully to the old behavior (rate ~= 0) for straight-line
   movers, so no expected regression against the previous two rungs'
   nearly-stationary opponents.
3. Verified `javac -cp libs/robocode.jar -d robots robots/custom/MyTank.java`
   compiles clean, no errors/warnings, `.class` up to date. Old version
   preserved at `archive/round1_backups/MyTank.java.before_round9_smoothing`
   for a quick diff/revert if next round's numbers regress.

### What I did NOT get to
- **Not validated by a real match** (same long-standing limitation every
  round hits — no working local headless battle runner in this sandbox; see
  round 6's section above for the most detailed writeup of exactly where that
  effort gets stuck). This is a real, previously-untested behavioral change
  to the gun-prediction math, so treat it with the same caution as rounds
  7-8's bullet-power tuning changes: check next round's accuracy stat closely
  before assuming it's a strict improvement.
- Did not touch bullet power bands or `PREFERRED_DISTANCE` this round —
  those were tuned/validated specifically against the *previous* rung's very
  passive opponent (`it_economics__ite_bomax`); since we're already winning
  overwhelmingly against this round's new, more-mobile opponent too, I didn't
  want to stack an unvalidated distance/power change on top of an unvalidated
  prediction-smoothing change in the same round — easier to isolate which
  change (if any) caused a regression if only one thing changed at a time.
- Did not dig further into *why* accuracy is 42% specifically (e.g. by
  checking whether misses cluster at specific distance bands, or specifically
  right after the opponent's sharp turns) — the `bh`-change analysis above
  was a quick manual check of one game (`sim_0.jsonl`), not a systematic
  script. A good next step: extend `tools/analyze_sim_logs.py` (or write a
  new script) to bucket hit/miss-implied bullet outcomes by opponent turn
  rate at time of firing, to more rigorously confirm/refute the "sharp turns
  cause misses" hypothesis this round's change is based on.

### Suggestions for next teammate
1. **First step, as always**: check the newest `/logs/rounds/<N>/trace.md`.
   - If it's still `trex22__deepthought` and accuracy is >= ~42% (this
     round's baseline) with 100% win rate still holding, the smoothing
     change is at worst neutral, at best an improvement — keep it.
   - If accuracy drops noticeably, revert via
     `archive/round1_backups/MyTank.java.before_round9_smoothing` and
     consider whether the smoothing constant (currently a flat 50/50 blend)
     needs adjusting instead of full reversion (e.g. weight the new sample
     less, like 0.3, if the opponent turns out to do a lot of *sustained*
     curving that the smoothing is now under-reacting to).
2. Run `python3 tools/analyze_freezes.py /logs/rounds/<N> --threshold 100 |
   grep -i sonnet` as a standard regression check for the wall/radar freeze
   bug classes — should print nothing.
3. If the opponent changes rungs again to something genuinely aggressive
   and *accurate* (every opponent seen across all rounds documented in this
   file so far has had <10% accuracy against us), that would be the first
   real stress test of the defensive/dodging side of the bot rather than
   just offense — worth watching `avg min energy` and win rate very closely
   in that case, since all our tuning so far has been offense-focused
   (bullet power, targeting) precisely because no opponent has punished us
   defensively yet.
4. Local headless battle-runner: still unresolved after 8+ rounds of
   attempts (see round 6's section for the most detailed known blocker,
   `RepositoryManager.loadSelectedRobots` not seeing a freshly-reloaded
   repository within the same call). Still the single highest-leverage infra
   fix available if a future teammate has a larger step budget to spend on
   it than usual.

## Round 10 update (this round) — validated round 9 smoothing, tightened fire-angle threshold for accuracy

### Context
Only `/logs/rounds/0/` and `/logs/rounds/1/` exist in this environment for me.
Both are real combat (verified via `python3 tools/analyze_sim_logs.py`) against
`trex22__deepthought` — same opponent round 9's notes describe, so round 1's
logs are the actual real-match validation of round 9's turn-rate-smoothing
change to the gun prediction (which was previously unvalidated). Result:
**100% win rate both rounds** (250/250 each). Accuracy: round 0 (pre-round-9
smoothing outcome, per its own numbers) 42%, round 1 (post-smoothing, i.e. the
real result of round 9's change) 41% — essentially flat/no regression, so the
smoothing change is validated as safe (neither a clear win nor loss on
accuracy against this particular opponent, but no harm, and it was a
theoretically sound defense against the noisy single-tick-estimate failure
mode described in round 9's notes, so keeping it).

Ran `python3 tools/analyze_freezes.py /logs/rounds/1 --threshold 100 | grep -i
sonnet` -> **zero matches**, confirming the wall-standoff (round 3) and
radar-freeze (round 4) fixes are still holding with no regression across many
rounds now.

### Change made this round: distance-scaled firing angle threshold
Previously, `onScannedRobot()` fired whenever `getGunHeat() == 0 &&
Math.abs(gunTurn) < 0.2` (a flat ~11.4 degree tolerance), regardless of range.
At long range this is a large positional slop: at 550px, 0.2 rad of angular
error corresponds to ~109px of actual miss distance — several multiples of a
robot's ~18px half-width — so a lot of shots were plausibly being released
while clearly still aimed off to the side, wasting energy without landing.
Since accuracy (41-42%) is currently our best lever for improving score
without risking movement/defense regressions (win rate is already 100% and
freezes are clean), and this rung's opponent (`trex22__deepthought`) is more
mobile/erratic than the last two rungs (see round 9's notes on its zigzag
movement pattern), tightening *when* we fire — not changing the prediction
math itself — seemed like a good, low-risk lever to pull.

Replaced the flat `0.2` threshold with a threshold sized to the target's
actual angular half-width at the current distance
(`atan(20.0 / distance) * 1.3`), clamped to `[0.03, 0.22]`. This keeps the old
generous tolerance at close range (where the target's angular size is
naturally large and any reasonable aim is a hit anyway) while shrinking the
tolerance considerably at long range (where precision actually matters), so
we should skip firing on ticks where the gun is still clearly swinging past
the target and instead fire on the tick(s) where the lead angle is actually
converged — the gun keeps turning every tick regardless of whether we fire, so
this should mostly just delay/withhold clearly-bad-angle shots rather than
meaningfully reduce total shot count against a target that stays in view for
many ticks (which is the common case per the logs — avg game is 400-500+
ticks with an opponent visible most of that time).

Verified `javac -cp libs/robocode.jar -d robots robots/custom/MyTank.java`
compiles clean (no errors/warnings), `.class` file up to date. Old
(pre-this-round) version preserved at
`archive/round1_backups/MyTank.java.before_round10_fire_threshold` for a quick
diff/revert if next round's accuracy or shot-volume numbers look worse.

### What I did NOT get to
- **Not validated by a real match** (same long-standing limitation as every
  previous round — no working local headless battle runner in this sandbox;
  see round 6's section for the most detailed writeup of exactly where that
  effort gets stuck). This is a real, previously-untested behavior change, so
  treat with the same caution as rounds 7-9's tuning changes: check next
  round's accuracy AND avg-shots-per-game numbers closely. If avg shots drops
  a lot while accuracy doesn't rise correspondingly, the threshold may be
  slightly too tight (e.g. try lowering the `1.3` margin factor toward `1.0`,
  or raising the `0.03` floor) rather than fully reverting.
- Did not touch bullet power bands, `PREFERRED_DISTANCE`, movement/strafe
  timing, or the circular-motion gun prediction/turn-rate-smoothing math
  itself this round — wanted to isolate this one change so it's easy to
  attribute any accuracy/score delta cleanly in next round's logs.
- Did not attempt the local headless-battle-runner fix again this round (see
  round 6's section for the most detailed known blocker,
  `RepositoryManager.loadSelectedRobots` not seeing a freshly-reloaded
  repository within the same call) — still unresolved after 9+ rounds of
  attempts, still the single highest-leverage infra fix available if a future
  teammate has a larger step budget to spend on it than usual.

### Suggestions for next teammate
1. **First step, as always**: check the newest `/logs/rounds/<N>/trace.md`.
   - If accuracy rose (or held steady) vs this round's baseline (41-42%) with
     100% win rate and similar-or-higher avg shots/game, the fire-threshold
     tightening is validated — consider tightening further (lower the
     `0.03`/`0.22` clamp bounds or the `1.3` margin factor) if there's still
     room, or leave as-is if returns have plateaued.
   - If avg shots/game craters (bot becomes overly hesitant to fire) without
     a compensating accuracy jump, or win rate/score drops, revert via
     `archive/round1_backups/MyTank.java.before_round10_fire_threshold` (flat
     0.2 rad threshold).
2. Run `python3 tools/analyze_freezes.py /logs/rounds/<N> --threshold 100 |
   grep -i sonnet` as a standard regression check for the wall/radar freeze
   bug classes — should print nothing if healthy.
3. If the ladder rung changes to a new opponent, re-baseline before assuming
   any of the tuning changes from rounds 7-10 (bullet power, preferred
   distance, fire-angle threshold) are still net-positive — they were all
   tuned/validated against specific (fairly weak/passive-to-moderately-mobile)
   opponents, and a genuinely strong, accurate opponent might respond
   differently.
4. Local headless battle-runner: still unresolved after 9+ rounds of attempts
   (see round 6's section for the most specific known blocker). Still the
   single highest-leverage infra fix available if anyone has spare steps to
   dig into `RepositoryManager.loadSelectedRobots`/`checkDbExists`/`reload`.

## Round 11 update (this round) — new tougher opponent (pez__gf1), added energy-management tuning + freeze-tool fix

### Context
Only `/logs/rounds/0/` exists in this environment for me. Per `trace.md` /
`results.json`, this round's opponent is a **new, noticeably tougher**
opponent, `pez__gf1` (different from all opponents documented in rounds 1-10
above). Confirmed real combat via `python3 tools/analyze_sim_logs.py
/logs/rounds/0` (2 robots, bullets, movement present). Result: **64% win rate
(160/250)**, 22% for `pez__gf1`, **34 ties (13.6%)** — by far the highest tie
rate of any round in this file's history (previous rounds: 0 losses/0 ties or
very few). Our accuracy dropped to **17%** (vs 41-70% against previous, much
weaker/more passive opponents) and avg min energy dropped to **24** (vs
86-96 previously) — this is the first opponent that's genuinely competitive:
it moves (avg speed 4.1) and lands real hits on us (their listed accuracy is
13%, similar ballpark to ours, unlike every previous opponent's <10%). Team
score is still solidly in our favor (23998 vs 12879) so we're still net
ahead, but this is a real step down in dominance vs previous rungs and is
worth taking seriously.

### Investigation: are the ties a bug, or just a hard opponent?
First checked for regressions using `tools/analyze_freezes.py` (the standard
health check from rounds 3/4/6/8/9/10's notes) — **found it was reporting 140
false-positive "freeze" findings on our own bot**, but on closer inspection
(wrote an ad-hoc script checking robot status `s` at the end of each flagged
freeze streak) **all 140 were explained by the robot being `DEAD`** (i.e. a
dead robot's last x/y/rh trivially stop changing — not a bug at all, just the
tool not accounting for death). **Fixed `tools/analyze_freezes.py`** to skip
any freeze streak whose robot status at the end of the streak is `DEAD`, so
future teammates get a clean signal instead of noise. Re-ran after the fix:
zero findings on `sonnet_5` across all 250 games (all 45 remaining findings
are on the opponent, and even those aren't obviously death-related — didn't
dig further since they're on the opponent, not us). **Conclusion: no
wall-standoff or radar-freeze regression** — the round-3/round-4 fixes are
still holding. The tie rate is a real behavioral/tactical issue, not a latent
bug resurfacing.

Manually inspected one tie game (`sim_4.jsonl`, 1465 turns — very long).
Both robots' energy visibly ground down to 0.0 by around turn 800-1200, both
robots then sat frozen (0 velocity, can't fire/move meaningfully at 0
energy) for the rest of the very long game until finally both showing `DEAD`
at the very end (t=1464). This looks like **mutual energy exhaustion**: both
sides spent down their energy (via missed/low-accuracy shots, which cost
energy up front regardless of hit/miss — see `Rules.html`'s bullet power
cost) faster than either landed a decisive kill, and once both hit ~0 there
was no way back (no easy income of energy in this ruleset apart from
landing more hits, which neither could do anymore). This is consistent with
the accuracy drop: at ~17% hit rate, a rough expected-value calculation using
Robocode's actual damage formula (`damage = 4*power + 2*max(0,power-1)` on a
hit, cost = `power` spent regardless of hit or miss; see `Rules.html`/
`Rules.RAMMING`-adjacent constants) shows firing at *any* power level has a
close-to-breakeven-or-negative expected net energy return around 17%
accuracy (rough breakeven is around p ~= 1/6 = 16.7% for power in (1,3]) —
i.e. we may have literally been firing at a rate where the "expected" outcome
of a shot is spending slightly more energy than it earns back in damage, on
average, against this specific harder-to-hit opponent. This doesn't mean
"never fire" (accuracy is an average — plenty of individual shots are still
much better than that when the angle is tight — and abstaining entirely would
forfeit all offense) but it does suggest **energy-management triage** is
worth adding: don't keep spending at a flat/naive rate regardless of how the
energy race is going.

### Change made this round: energy-aware bullet power (`robots/custom/MyTank.java`)
In `onScannedRobot()`, right after computing the distance-based
`bulletPowerForDistance()` value, added two override cases:
1. **Finishing triage**: if the enemy's remaining energy (`e.getEnergy()`) is
   `<= 16` (i.e. a single power-3 hit, dealing up to 16 damage, could kill
   them outright), always use max power (3.0) regardless of distance — worth
   the extra spend to try to close out the kill and deny them a chance to
   recover, rather than a diluted long-range-conservation shot.
2. **Survival triage**: if OUR OWN energy (`getEnergy()`) is `<= 15` AND the
   enemy is NOT already in the immediate-kill-range case above (i.e. this is
   a real ongoing race, not "we're both nearly dead and I should go for the
   kill"), throttle bullet power down to a cheap 1.0 instead of continuing to
   fire full-price shots into a fight we're not favored to win purely by
   volume — conserves energy for survival (and for ramming opportunities,
   which are "free" 1.8-for-0.6 damage trades per `Rules.html`'s
   `ROBOT_HIT_BONUS`/`ROBOT_HIT_DAMAGE` constants, not yet exploited in this
   bot's `onHitRobot()`, which still only defends/backs away — a good next
   step for a future teammate with more time, see below) rather than racing
   to 0 on a bet we're statistically not favored to win at our current
   accuracy against this opponent.
Both thresholds are intentionally simple/coarse (no distance dependence,
just two energy checks) — this is meant as basic triage, not a rewrite of
the targeting math, and should be very low risk: it only changes behavior in
the specific edge cases of "enemy nearly dead" or "we're nearly dead", not
normal mid-fight firing.

Verified `javac -cp libs/robocode.jar -d robots robots/custom/MyTank.java`
compiles clean (no errors/warnings), `.class` up to date. Old version
preserved at `archive/round1_backups/MyTank.java.before_round11_energy_mgmt`
for a quick diff/revert if next round's numbers look worse (e.g. if tie rate
or loss rate goes UP, or accuracy/score drops).

### What I did NOT get to
- **Not validated by a real match** (same long-standing limitation as every
  previous round — no working local headless battle runner in this sandbox;
  see round 6's section for the most detailed writeup of exactly where that
  effort gets stuck). Treat this change with the same caution as every
  previous round's real (non-bugfix) tuning change: check next round's tie
  rate, loss rate, and avg-min-energy numbers closely. If tie rate is still
  high or gets worse, the thresholds (16 / 15) may need adjusting, or a
  more fundamental fix (e.g. actual wave-surfing dodge to reduce damage
  *taken*, or exploiting ramming for cheap damage — see below) may be needed
  instead of just throttling our own offense.
- **Did NOT implement deliberate ramming-for-damage.** Per `Rules.html`,
  ramming an opponent by moving into them deals `ROBOT_HIT_BONUS = 1.2` bonus
  damage to them (on top of the base `ROBOT_HIT_DAMAGE = 0.6` both sides
  take from any robot-robot collision) — a genuinely "profitable" trade (1.8
  dealt for 0.6 taken) if you're the one actively driving into contact,
  totally free of bullet-energy cost. Our current `onHitRobot()` always backs
  away defensively regardless of the energy race state, and there's no
  offensive-ramming logic anywhere in `run()`/`onScannedRobot()`'s movement.
  Given this round's finding that we may be firing at close to
  breakeven-or-negative EV against this tougher opponent, deliberately ramming
  when already very close (e.g. inside ~40px) instead of always avoiding
  contact could be a meaningfully better lever than bullet-power throttling
  alone — did not have enough remaining steps this round to design and test
  this safely (risk: could interact badly with the existing wall-avoidance /
  stuck-watchdog logic if not careful, e.g. mistaking "intentionally pressing
  into the enemy" for "stuck against a wall").
- Did not touch movement/dodging (orbit strafing, `PREFERRED_DISTANCE`,
  wave-surfing) at all — all of this round's opponent-side hit rate (their
  13% accuracy vs. our being hit down to avg min energy 24) suggests our
  *defense* may also be a bigger lever than offense against this specific
  opponent, but that's a much bigger, riskier change (see round 1's original
  suggestion of wave-surfing) than I wanted to attempt with the time left
  this round given it's untested locally either way.

### Suggestions for next teammate
1. **First step, as always**: check `/logs/rounds/<N>/trace.md` for this
   round's real result.
   - If tie rate drops and/or accuracy/score improves vs this round's
     baseline (64% win / 22% opp / 14% tie, 17% accuracy, min energy 24,
     score 23998 vs 12879), the energy-triage change is validated — consider
     extending the idea (e.g. tune the 16/15 thresholds, or add the
     ramming-for-damage idea above).
   - If tie rate goes UP or a clear regression appears, revert via
     `archive/round1_backups/MyTank.java.before_round11_energy_mgmt` and
     consider a different lever (e.g. defense/dodging improvements instead of
     offense throttling).
2. Run `python3 tools/analyze_freezes.py /logs/rounds/<N> --threshold 100 |
   grep -i sonnet` (now fixed to ignore expected end-of-life freezes, see
   above) as the standard regression check — should print nothing if healthy.
3. Seriously consider implementing deliberate close-range ramming (see "What
   I did NOT get to" above) if this opponent (or a similarly tough future
   one) keeps showing up — it's a mechanically free damage source per
   `Rules.html` that the bot has never exploited in 10 rounds of history, and
   this round's analysis suggests our bullet-only offense may be running
   close to breakeven EV against tougher/more mobile opponents.
4. If `pez__gf1` keeps appearing, consider a proper accuracy/miss-clustering
   analysis (e.g. bucket misses by opponent recent turn-rate/speed at time of
   firing) to see if the round-9/10 circular-prediction + fire-threshold
   logic specifically struggles against this opponent's movement style, vs.
   it just being a genuinely hard target for any linear/circular predictive
   aiming.
5. Local headless battle-runner: still unresolved after 10+ rounds of
   attempts (see round 6's section for the most detailed known blocker,
   `RepositoryManager.loadSelectedRobots` not seeing a freshly-reloaded
   repository within the same call). Still the single highest-leverage infra
   fix available if a future teammate has a larger step budget to spend on
   it than usual.

## Round 12 update (this round) — corrected energy-swing math + opportunistic ramming

### Context
Only `/logs/rounds/0/` and `/logs/rounds/1/` exist in this environment for me.
Both real combat (confirmed via `tools/analyze_sim_logs.py`) against `pez__gf1`
— same tougher opponent round 11's notes describe. Round 1's logs are the real
match result of round 11's "energy management" change (throttle bullet power
to 1.0 when our own energy is low). Result: **62% win rate** (155/250), 24%
`pez__gf1`, **35 ties (14%)** — essentially flat vs round 0's 64%/22%/14%
baseline (pre-round-11-change), team score barely changed (22951 vs 22998).
So round 11's change was net neutral-to-slightly-negative, not the
improvement it was hoping for. `analyze_freezes.py` confirms no wall/radar
freeze regressions (zero findings on `sonnet_5` in round 1's logs) — the tie
problem is a real tactical issue (mutual energy grinding to 0 in very long
700-1500+ turn games), not a latent bug.

### Key finding: round 11's energy-throttle reasoning was mathematically backwards
Worked out the actual Robocode economics from `Rules.class` (decompiled via
`javap`, see below) rather than guessing:
- `Rules.getBulletHitBonus(power) == 3 * power` — the SHOOTER gets 3x the
  bullet's power refunded as energy on a hit (on top of the damage dealt to
  the target), a mechanic none of rounds 1-11's notes had accounted for.
- `Rules.getBulletDamage(power) == 6*power - 2` for power > 1 (`4*power` for
  power <= 1).
- Combining these, the **relative energy swing per shot** (our energy minus
  their energy, positive = good for us) at hit probability `p` and power `P`
  works out to:
  ```
  swing(P, p) = p*(9P - 2) - P
  ```
  At our actual measured accuracy against this opponent (`p ~= 0.17`),
  `swing(P)` is **increasing** in `P` for every power level we actually use
  (1.5/2.2/2.9/3.0) — i.e. **higher bullet power is better for us at this
  accuracy, not worse**. Round 11's "throttle down to power 1.0 when our own
  energy is low" heuristic had exactly the wrong sign: it reduced our
  relative-swing efficiency precisely in the situation (already behind) where
  we could least afford to fall further behind. This matches the observed
  flat/slightly-worse real result above.
- Decompile command used, if a future teammate wants to double check other
  constants: `jar xf libs/robocode.jar robocode/Rules.class && javap -p -c
  robocode/Rules.class` (from a scratch dir). Also useful:
  `robocode/Rules.ROBOT_HIT_DAMAGE = 0.6`, `ROBOT_HIT_BONUS = 1.2`
  (documented directly in `javadoc/robocode/Rules.html`, no decompiling
  needed for those two).

### Changes made this round (`robots/custom/MyTank.java`)
1. **Removed** the round-11 "own energy low -> throttle bullet power to 1.0"
   rule (shown above to be counter-productive). Kept the "enemy energy <= 16
   -> always max power" finishing rule (that one was always directionally
   correct per the same math — higher power is better at our accuracy
   regardless of whose energy triggered the check).
2. **Added** a new "press the advantage" rule: if `getEnergy() - e.getEnergy()
   > 15` (we have a clear energy lead), use max power (3.0) rather than the
   normal distance-scaled power, to close out fights faster instead of
   letting a long-range grind continue into a potential tie. This is the
   mirror-image, better-reasoned version of what round 11 was trying to do.
3. **New: opportunistic ramming.** Per the `ROBOT_HIT_DAMAGE`/`ROBOT_HIT_BONUS`
   constants above, initiating robot-robot contact nets the initiator a
   relative swing of **+1.2 per collision** (they lose 0.6+1.2=1.8, we lose
   0.6) — a better swing-per-action than almost any bullet at our current
   ~17% accuracy, and it costs zero gun energy/heat. Previously the bot's
   movement logic only ever tried to hold `PREFERRED_DISTANCE` or flee
   (`onHitRobot` always backed away), leaving this essentially free damage
   source completely unused. Added:
   - In `onScannedRobot()`: if `enemyDistance < 60 && getEnergy() > 3`, skip
     normal orbit movement for that tick and instead turn toward + charge the
     enemy (`setAhead(enemyDistance + 20)`) to force/continue contact. This
     check sits *after* the existing stuck-watchdog/wall-avoidance early
     returns, so it never fights those (they still take precedence).
   - `onHitRobot()`: now presses forward into the opponent (`setAhead(40)`
     toward their bearing) when `getEnergy() > 8`, instead of always backing
     away — the collision damage already happened by the time this event
     fires regardless of what we do next, so retreating was pure lost
     opportunity for repeat contact damage. Falls back to the old
     retreat-toward-field-center behavior only when critically low on energy
     (`<= 8`), for safety.
4. Verified `javac -cp libs/robocode.jar -d robots robots/custom/MyTank.java`
   compiles clean (no errors/warnings), `.class` up to date. Old
   (pre-this-round) version preserved at
   `archive/round1_backups/MyTank.java.before_round12_ram_tuning` for a quick
   diff/revert if next round's numbers look worse.

### What I did NOT get to
- **Not validated by a real match** (same long-standing limitation as every
  previous round — no working local headless battle runner in this sandbox).
  This round's changes are real behavioral changes (not pure bugfixes), so
  treat with normal caution: check next round's tie rate, win rate, and
  avg-rams/game closely.
  - **Specific risk to watch for with the ramming change**: charging directly
    at the enemy when within 60px necessarily means closing distance, which
    could expose us to more return fire at point-blank range if this
    opponent's accuracy is *also* better up close (symmetric risk — but our
    own fire-angle threshold logic, round 10, is also more generous up close,
    so we should out-shoot them at range too). If avg-min-energy or loss rate
    gets *worse* next round, this is the first thing to check/revert.
  - **Risk to watch for with "press the advantage" (max power when
    getEnergy() - e.getEnergy() > 15)**: slower bullets (higher power = lower
    bulletSpeed) could reduce hit rate specifically in these already-winning
    situations. If accuracy drops noticeably in games we're clearly ahead in,
    consider tuning the threshold or reverting this one piece.
- Did not touch `PREFERRED_DISTANCE`, the fire-angle threshold (round 10), or
  the circular-motion gun prediction/turn-rate smoothing (rounds 5/9) at all
  this round — wanted to isolate the energy-math correction + ramming as one
  coherent, well-reasoned change this round rather than stacking multiple
  unrelated tweaks.
- Did NOT do a rigorous per-shot accuracy-vs-power correlation analysis on the
  real logs (would require correlating each bullet's `p` field in `sim_*.jsonl`
  with whether it eventually hit, which isn't trivially available per-bullet
  in the current log format without tracking bullet trajectories tick-by-tick
  to their disappearance) — the `swing(P,p)` formula above is a sound
  first-principles argument from the documented game rules, but a future
  teammate with more steps could build a script to empirically verify it
  against real per-shot outcomes if the numbers don't move as expected.

### Suggestions for next teammate
1. **First step, as always**: check `/logs/rounds/<N>/trace.md` for this
   round's real result.
   - If tie rate drops below ~14% and/or win rate/score improves vs this
     round's baseline (62% win / 24% opp / 14% tie, score 22951 vs 13591),
     both the energy-math fix and ramming change are validated — consider
     tuning the ramming trigger distance (currently 60px) up a bit, or
     lowering the "press advantage" threshold (currently 15 energy) to
     trigger the max-power finishing push more often.
   - If avg-rams/game goes up a lot but win rate/score doesn't improve (or
     gets worse), the ramming change may be exposing us to more return fire
     than it's worth — consider reverting via
     `archive/round1_backups/MyTank.java.before_round12_ram_tuning` or
     tightening the trigger (e.g. only ram when we also have an energy edge,
     not unconditionally within 60px).
   - If accuracy drops specifically in high-energy-lead games, revisit the
     "press the advantage" max-power override.
2. Run `python3 tools/analyze_freezes.py /logs/rounds/<N> --threshold 100 |
   grep -i sonnet` as the standard regression check for the wall/radar freeze
   bug classes (rounds 3/4) — should print nothing if healthy.
3. If ties are still common after this round's changes, the next lever to
   pull is probably actual defense/dodging improvement (this bot's movement
   has never been validated against an opponent with real (13-15%+) accuracy
   before this rung — see round 11's notes for the original observation that
   `pez__gf1` is the first opponent across 11 rounds of history to land hits
   on us at a comparable rate to our own accuracy). A proper wave-surfing
   dodge (tracking incoming-bullet-implied danger zones rather than a fixed
   perpendicular orbit) has been suggested since round 1 and still hasn't
   been attempted — this would reduce damage *taken* rather than trying to
   increase damage *dealt* further, which is the side of the ledger every
   round so far (5, 7-12) has focused on instead.
4. Local headless battle-runner: still unresolved after 11+ rounds of
   attempts (see round 6's section for the most detailed known blocker,
   `RepositoryManager.loadSelectedRobots` not seeing a freshly-reloaded
   repository within the same call). Still the single highest-leverage infra
   fix available if a future teammate has a larger step budget to spend on
   it than usual.
