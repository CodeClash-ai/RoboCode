#!/usr/bin/env python3
"""
analyze_power_accuracy.py -- bucket bullet outcomes (hit / miss) by bullet
power and by the shooter's own robot name, from sim_*.jsonl logs.

Motivation (see README_agent.md rounds 17/18 notes): several rounds'
bullet-power tuning decisions were based on first-principles reasoning about
Rules.getBulletHitBonus/getBulletDamage math and manual single-game traces,
but never a rigorous empirical "does accuracy actually vary with power"
check across a full 250-game sample. This script provides that.

IMPORTANT LOG-FORMAT GOTCHAS (read before touching this script again --
cost real debugging time across many attempts to get this right; see
README_agent.md rounds 22/28/112/113 for the full history):

1. (round 22) A bullet's entry in the 'b' list does NOT disappear from the
   log the tick after it resolves (hits something / hits a wall / collides
   with another bullet). Once a bullet's status flips to a terminal value
   (HIT_VICTIM / HIT_WALL / EXPLODED), an entry with that SAME status keeps
   getting logged for MANY subsequent ticks (observed 80+ ticks after the
   real hit). Two earlier attempts at this script tried to track bullets
   frame-to-frame THROUGH their terminal state and both badly overcounted
   "shots" because of this.

2. (round 28) Some sim_*.jsonl files log every game tick (t increments by 1
   each line) while others only log every OTHER tick (t increments by 2),
   and a few even mix both step sizes within one file. Any distance-based
   continuity matching must scale its expected-travel-distance and
   tolerance by however many ticks actually elapsed since a track was last
   matched (read the log's own "t" field), not assume a fixed step of 1.

3. (round 113 -- NEW, found this round, root-caused what round 112 had only
   flagged as an unexplained ">100% accuracy" anomaly in one bucket) The
   HIT_VICTIM "ghost" frame described in gotcha #1 does not just sit at a
   fixed position for all those extra ticks -- it visibly DRIFTS, and the
   drift is NOT random rendering noise: it tracks the VICTIM robot's own
   subsequent movement (i.e. the impact marker stays "glued" to the point
   on the victim where the bullet struck, and follows the victim around as
   the victim keeps moving for the rest of the game). Confirmed by direct
   inspection of real logs (round-113 investigation): a single real hit's
   HIT_VICTIM entry can drift by several px/tick, sometimes even matching
   the victim's own instantaneous velocity, for 20-80+ consecutive ticks
   after the actual hit. The OLD version of this script's hit-counting
   logic (Part 2, "hits via energy-drop matching") treated EVERY tick that
   ANY HIT_VICTIM entry for a given (owner, power) was present as an
   independent opportunity to attribute a fresh hit (deduped only by
   rounded (x, y), which changes every tick precisely BECAUSE the ghost
   drifts) -- so a single real hit could get wrongly re-attributed to any
   later, unrelated small energy-drop event (e.g. from a completely
   different attacker, or from inactivity decay accumulating) for as long
   as that one ghost frame kept drifting, as long as the drop happened to
   be within ENERGY_TOLERANCE of that (owner, power)'s damage formula. This
   silently OVER-counted hits, most visibly for LOW-power buckets (small
   bulletDamage, e.g. 2.0 for power=0.5) since small energy drops from
   unrelated causes are common and easily fall within tolerance of a small
   target damage value -- exactly the symptom round 112 found (hits >
   shots, "111.0% accuracy", in the round-109-introduced 0.5-1.0 power
   bucket specifically, the first bucket in this file's history to see
   sustained heavy use of such a low bullet power).

   THE FIX (this round): stop scanning the raw HIT_VICTIM list for hit
   attribution entirely. Instead, reuse the SAME frame-to-frame MOVING-track
   bookkeeping already built for shot-counting (Part 1) to detect the
   precise, one-time TRANSITION moment a tracked bullet stops appearing as
   MOVING and a terminal-status entry shows up near where that track would
   have travelled to (within the same speed/tolerance window already
   trusted for continuity matching). That single tick is the bullet's real,
   one-and-only resolution event; only THAT tick/position is eligible for
   hit attribution, closing off the entire "give a stale ghost frame another
   chance every subsequent tick" failure mode. (Known, accepted limitation:
   a bullet that resolves in the very same tick it's first fired --
   essentially point-blank -- without ever appearing as a tracked MOVING
   frame first won't be resolved this way and could be undercounted; this
   is a rare edge case and a far smaller, safer error than the overcounting
   bug it replaces.)

Usage:
    python3 tools/analyze_power_accuracy.py /logs/rounds/<N> [--bucket-width 0.5]

Prints a table of shots/hits/accuracy per (robot, power-bucket), plus a
sanity-check total-shots-per-game figure -- compare against trace.md's
"avg shots" column (summed across both robots) as a health check. If it's
off by more than modest variance, something has regressed / the log format
changed again and this script needs another careful look before trusting
the per-bucket numbers.
"""
import argparse
import glob
import json
import os
import sys
from collections import defaultdict

TERMINAL_STATUSES = {"HIT_VICTIM", "HIT_WALL", "EXPLODED"}
SPEED_TOLERANCE = 1.5  # px/tick slack around the exact bulletSpeed formula
ENERGY_TOLERANCE = 0.35  # slack when matching an energy drop to a damage formula
RESOLUTION_DIST_BUFFER = 25.0  # extra px allowance (round 113) when matching a
# vanished MOVING track to this tick's terminal-status entry -- collision
# detection triggers when the bullet enters the victim's ~36px-wide hitbox,
# not exactly at a perfect straight-line continuation of the last tracked
# MOVING position, so real resolutions can land a bit further (or closer)
# than SPEED_TOLERANCE alone would allow. Safe to be generous here since this
# check only ever fires ONCE per track (the single tick it vanishes), unlike
# the old code's unbounded-lingering-ghost-frame rescan (see gotcha #3).


def bullet_speed(power):
    return 20.0 - 3.0 * power


def bullet_damage(power):
    if power <= 1.0:
        return 4.0 * power
    return 6.0 * power - 2.0


def bucket_power(power, width):
    lo = int(power / width) * width
    hi = lo + width
    return f"{lo:.1f}-{hi:.1f}"


class MovingTrack:
    __slots__ = ("x", "y", "power", "expected_speed", "seen_this_tick", "last_tick")

    def __init__(self, x, y, power, tick):
        self.x = x
        self.y = y
        self.power = power
        self.expected_speed = bullet_speed(power)
        self.seen_this_tick = True
        self.last_tick = tick


def analyze(logdir, bucket_width):
    files = sorted(glob.glob(os.path.join(logdir, "sim_*.jsonl")))
    if not files:
        print(f"No sim_*.jsonl files found in {logdir}", file=sys.stderr)
        return 1

    stats = defaultdict(lambda: defaultdict(lambda: [0, 0]))  # [shots, hits]
    total_games = 0
    total_shots_all_robots = 0

    for path in files:
        robots = {}
        moving_tracks = defaultdict(list)  # owner -> list[MovingTrack]
        prev_energy = {}  # robot index (str) -> energy

        with open(path) as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                d = json.loads(line)
                if "robots" in d:
                    robots = d["robots"]
                    continue

                cur_tick = d.get("t")

                # --- Part 1: shots-fired via MOVING-only tracking, AND
                # detect the one-time MOVING -> terminal resolution moment
                # for each tracked bullet (round 113 fix -- see docstring
                # gotcha #3) ---
                bullets_by_owner = defaultdict(list)
                for b in d.get("b", []):
                    bullets_by_owner[b.get("o")].append(b)

                # resolved_this_tick[(owner, power)] -> list of (x, y, status)
                # for bullets whose MOVING track just vanished this tick and
                # was matched to a terminal-status entry -- these are the
                # ONLY events eligible for hit attribution in Part 2 below.
                resolved_this_tick = defaultdict(list)

                for owner, blist in bullets_by_owner.items():
                    moving_list = [b for b in blist if b.get("s") == "MOVING"]
                    terminal_list = [b for b in blist if b.get("s") in TERMINAL_STATUSES]
                    owner_tracks = moving_tracks[owner]
                    for t in owner_tracks:
                        t.seen_this_tick = False

                    # Global greedy matching (round 28 fix) for MOVING
                    # continuity, unchanged from before.
                    candidates = []
                    for bi, b in enumerate(moving_list):
                        bx, by, power = b["x"], b["y"], b.get("p", 0.0)
                        for ti, t in enumerate(owner_tracks):
                            if abs(t.power - power) > 1e-6:
                                continue
                            elapsed = 1
                            if cur_tick is not None and t.last_tick is not None:
                                elapsed = max(1, cur_tick - t.last_tick)
                            dist = ((t.x - bx) ** 2 + (t.y - by) ** 2) ** 0.5
                            err = abs(dist - t.expected_speed * elapsed)
                            tol = SPEED_TOLERANCE * elapsed
                            if err <= tol:
                                candidates.append((err, bi, ti))
                    candidates.sort(key=lambda c: c[0])
                    bullet_matched = [False] * len(moving_list)
                    track_matched = [False] * len(owner_tracks)
                    for err, bi, ti in candidates:
                        if bullet_matched[bi] or track_matched[ti]:
                            continue
                        bullet_matched[bi] = True
                        track_matched[ti] = True
                        b = moving_list[bi]
                        t = owner_tracks[ti]
                        t.x, t.y = b["x"], b["y"]
                        t.seen_this_tick = True
                        t.last_tick = cur_tick
                    unmatched = [b for bi, b in enumerate(moving_list) if not bullet_matched[bi]]
                    for b in unmatched:
                        # Genesis of a new shot.
                        power = b.get("p", 0.0)
                        owner_name = robots.get(str(owner), str(owner))
                        bucket = bucket_power(power, bucket_width)
                        stats[owner_name][bucket][0] += 1
                        total_shots_all_robots += 1
                        nt = MovingTrack(b["x"], b["y"], power, cur_tick)
                        owner_tracks.append(nt)

                    # Round 113 fix: resolve vanished (no-longer-MOVING)
                    # tracks against this tick's terminal-status entries,
                    # using the exact same continuity-distance window
                    # already trusted for MOVING matching (0 up to one more
                    # expected step + tolerance -- a bullet can resolve
                    # anywhere along its final step, not just at the full
                    # step's end). Each terminal entry can only resolve ONE
                    # vanished track (greedy, nearest distance first) so a
                    # single terminal frame can't double-count.
                    vanished = [ti for ti, t in enumerate(owner_tracks) if not t.seen_this_tick]
                    if vanished and terminal_list:
                        term_candidates = []
                        for ti in vanished:
                            t = owner_tracks[ti]
                            elapsed = 1
                            if cur_tick is not None and t.last_tick is not None:
                                elapsed = max(1, cur_tick - t.last_tick)
                            max_dist = t.expected_speed * elapsed + SPEED_TOLERANCE * elapsed + RESOLUTION_DIST_BUFFER
                            for bi, b in enumerate(terminal_list):
                                if abs(b.get("p", 0.0) - t.power) > 1e-6:
                                    continue
                                dist = ((t.x - b["x"]) ** 2 + (t.y - b["y"]) ** 2) ** 0.5
                                if dist <= max_dist:
                                    term_candidates.append((dist, ti, bi))
                        term_candidates.sort(key=lambda c: c[0])
                        track_resolved = set()
                        terminal_used = set()
                        for dist, ti, bi in term_candidates:
                            if ti in track_resolved or bi in terminal_used:
                                continue
                            track_resolved.add(ti)
                            terminal_used.add(bi)
                            t = owner_tracks[ti]
                            b = terminal_list[bi]
                            resolved_this_tick[(owner, t.power)].append(
                                (b["x"], b["y"], b.get("s")))

                    moving_tracks[owner] = [t for t in owner_tracks if t.seen_this_tick]

                # --- Part 2: hits via energy-drop matching, restricted to
                # ONLY this tick's freshly-resolved bullets (round 113 fix --
                # no longer scans/re-uses lingering terminal "ghost" frames
                # from earlier ticks, which is what caused the overcounting
                # bug described in gotcha #3 above) ---
                hitvictim_by_owner_power = defaultdict(list)
                for (owner, power), entries in resolved_this_tick.items():
                    for (x, y, status) in entries:
                        if status == "HIT_VICTIM":
                            hitvictim_by_owner_power[(owner, power)].append({"x": x, "y": y})

                units = {str(u["i"]): u for u in d.get("u", [])}
                for idx, u in units.items():
                    energy = u["e"]
                    prev = prev_energy.get(idx)
                    prev_energy[idx] = energy
                    if prev is None:
                        continue
                    drop = prev - energy
                    if drop <= 0.05:
                        continue
                    # Try to explain this drop as a bullet hit from some owner+power.
                    for (owner, power), blist in hitvictim_by_owner_power.items():
                        if str(owner) == idx:
                            continue  # can't hit yourself
                        if not blist:
                            continue
                        expected = bullet_damage(power)
                        if abs(expected - drop) > ENERGY_TOLERANCE:
                            continue
                        owner_name = robots.get(str(owner), str(owner))
                        bucket = bucket_power(power, bucket_width)
                        stats[owner_name][bucket][1] += 1
                        blist.pop(0)
                        break
        total_games += 1

    print(f"Analyzed {total_games} games in {logdir} (bucket width={bucket_width})")
    if total_games:
        print(f"Sanity check: {total_shots_all_robots} total counted shots across "
              f"all robots / {total_games} games = "
              f"{total_shots_all_robots / total_games:.1f} shots/game combined "
              f"(compare to trace.md's avg-shots columns summed across robots)\n")

    for robot in sorted(stats.keys()):
        print(f"=== {robot} ===")
        print(f"{'power bucket':>14} | {'shots':>6} | {'hits':>5} | {'accuracy':>8}")
        buckets = stats[robot]
        for bucket in sorted(buckets.keys(), key=lambda b: float(b.split("-")[0])):
            shots, hits = buckets[bucket]
            acc = (100.0 * hits / shots) if shots else 0.0
            print(f"{bucket:>14} | {shots:>6} | {hits:>5} | {acc:>7.1f}%")
        total_shots = sum(s for s, h in buckets.values())
        total_hits = sum(h for s, h in buckets.values())
        overall_acc = (100.0 * total_hits / total_shots) if total_shots else 0.0
        print(f"{'TOTAL':>14} | {total_shots:>6} | {total_hits:>5} | {overall_acc:>7.1f}%")
        print()

    # --- Round 131 addition: per-robot summed energy-swing(P,p) totals. ---
    # Motivation (see README_agent.md round 130's notes): individual per-bucket
    # accuracy numbers don't directly answer "is this matchup's bullet-power
    # tuning actually net-favorable overall" -- a bucket with low accuracy but
    # high power/volume can still dominate the total swing, or vice versa.
    # Round 12/109's swing(P,p) formula (derived from Rules.class's actual
    # getBulletDamage()/getBulletHitBonus() constants, not guessed) gives the
    # expected relative energy change per shot at accuracy p and power P:
    #   P <= 1:  swing = P * (7p - 1)              (flat 1/7 breakeven)
    #   P >  1:  swing = p * (9P - 2) - P           (breakeven depends on P)
    # Summing (swing-per-shot * shots) across every bucket gives a single,
    # directly comparable "total expected energy swing" per robot for the
    # whole sample -- the clearest available signal for "who's actually ahead
    # on bullet economics in this matchup", independent of any single
    # bucket's raw accuracy number.
    print("=== Per-bucket swing(P,p) totals (round 131) ===")
    print(f"{'robot':>24} | {'total swing':>12} | {'swing/game':>11}")
    for robot in sorted(stats.keys()):
        buckets = stats[robot]
        total_swing = 0.0
        for bucket, (shots, hits) in buckets.items():
            if shots <= 0:
                continue
            low = float(bucket.split("-")[0])
            power = low + bucket_width / 2.0
            p = hits / shots
            if power <= 1.0:
                sw = power * (7.0 * p - 1.0)
            else:
                sw = p * (9.0 * power - 2.0) - power
            total_swing += sw * shots
        per_game = total_swing / total_games if total_games else 0.0
        print(f"{robot:>24} | {total_swing:>12.1f} | {per_game:>11.2f}")
    print()
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("logdir", help="Path to a /logs/rounds/<N> directory")
    parser.add_argument("--bucket-width", type=float, default=0.5,
                         help="Width of power buckets (default 0.5)")
    args = parser.parse_args()
    sys.exit(analyze(args.logdir, args.bucket_width))
