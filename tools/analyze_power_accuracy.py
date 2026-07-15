#!/usr/bin/env python3
"""
analyze_power_accuracy.py -- bucket bullet outcomes (hit / miss) by bullet
power and by the shooter's own robot name, from sim_*.jsonl logs.

Motivation (see README_agent.md rounds 17/18 notes): several rounds'
bullet-power tuning decisions were based on first-principles reasoning about
Rules.getBulletHitBonus/getBulletDamage math and manual single-game traces,
but never a rigorous empirical "does accuracy actually vary with power"
check across a full 250-game sample. This script provides that.

IMPORTANT LOG-FORMAT GOTCHA (discovered round 22 -- read before touching this
script again, cost real debugging time across three attempts to work out):
a bullet's entry in the 'b' list does NOT disappear from the log the tick
after it resolves (hits something / hits a wall / collides with another
bullet). Once a bullet's status flips to a terminal value (HIT_VICTIM /
HIT_WALL / EXPLODED), an entry with that SAME status keeps getting logged
for many, many subsequent ticks (observed 80+ ticks after the real hit,
confirmed against the matching enemy energy-drop tick) with a position that
keeps drifting in a roughly-plausible-looking but NOT reliably
speed-consistent way (it does NOT keep moving at the bullet's real
bulletSpeed = 20-3*power once terminal -- it visibly slows down). This is
some kind of rendering/animation leftover in the log, not real game state.
Two earlier attempts at this script tried to track bullets frame-to-frame
THROUGH their terminal state (via a velocity-vector predictor, then via a
fixed-bulletSpeed-distance predictor) and both overcounted "shots" by
5-20x because of exactly this -- every lingering post-terminal frame kept
either falsely extending a track or (once it drifted far enough from the
too-tight expected step) spawning a brand new phantom "shot" that was
already terminal on its first frame.

The fix that actually works, used below: split the two questions apart
instead of trying to solve them with one unified tracker.
  1. "How many shots were fired (denominator), broken down by power?"
     Answered by ONLY tracking entries with status == "MOVING" (matched
     frame-to-frame via fixed bulletSpeed-distance continuity, same owner +
     same power). Any MOVING entry that doesn't match an existing open
     MOVING track is the genesis frame of a brand new shot -- count it once,
     right there, and never revisit it. Terminal-status entries are
     completely ignored for this purpose (both for matching purposes and for
     ever creating a track), so the "lingering ghost" frames described above
     can never spawn a phantom new shot or corrupt a track's continuity,
     since they're simply never looked at here at all.
  2. "How many of those shots were hits (numerator), and at what power?"
     Answered independently via each robot's own energy trace, which is a
     completely reliable, single, uncorrupted signal per Rules.html: a
     robot's energy only drops due to (a) taking bullet damage
     (4*power for power<=1, else 6*power-2), (b) ramming (fixed 0.6), or
     (c) inactivity decay (a small, slow, well-known drain). For each tick
     where a robot's energy drops by an amount matching the bullet-damage
     formula for some plausible power (within a small tolerance), and there
     is at least one *newly-appearing-this-tick* HIT_VICTIM entry from an
     opposing owner whose power matches that implied damage, attribute one
     hit to that owner+power. "Newly appearing" here means: not already
     seen as HIT_VICTIM from that owner+power at that exact position last
     tick either -- i.e. we still exploit terminal-frame de-duplication, but
     only to avoid re-attributing the SAME hit twice, not to try to track
     the bullet's ongoing (unreliable) position at all.

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
    __slots__ = ("x", "y", "power", "expected_speed", "seen_this_tick")

    def __init__(self, x, y, power):
        self.x = x
        self.y = y
        self.power = power
        self.expected_speed = bullet_speed(power)
        self.seen_this_tick = True


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
        # seen_hit_keys: (owner, power, round(x), round(y)) already attributed,
        # to dedupe the exact same hit if it happens to appear again.
        seen_hit_keys = set()

        with open(path) as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                d = json.loads(line)
                if "robots" in d:
                    robots = d["robots"]
                    continue

                # --- Part 1: shots-fired via MOVING-only tracking ---
                bullets_by_owner = defaultdict(list)
                for b in d.get("b", []):
                    bullets_by_owner[b.get("o")].append(b)

                for owner, blist in bullets_by_owner.items():
                    moving_list = [b for b in blist if b.get("s") == "MOVING"]
                    owner_tracks = moving_tracks[owner]
                    for t in owner_tracks:
                        t.seen_this_tick = False
                    unmatched = []
                    for b in moving_list:
                        bx, by, power = b["x"], b["y"], b.get("p", 0.0)
                        best = None
                        best_err = None
                        for t in owner_tracks:
                            if t.seen_this_tick:
                                continue
                            if abs(t.power - power) > 1e-6:
                                continue
                            dist = ((t.x - bx) ** 2 + (t.y - by) ** 2) ** 0.5
                            err = abs(dist - t.expected_speed)
                            if err <= SPEED_TOLERANCE and (best is None or err < best_err):
                                best = t
                                best_err = err
                        if best is not None:
                            best.x = bx
                            best.y = by
                            best.seen_this_tick = True
                        else:
                            unmatched.append(b)
                    for b in unmatched:
                        # Genesis of a new shot.
                        power = b.get("p", 0.0)
                        owner_name = robots.get(str(owner), str(owner))
                        bucket = bucket_power(power, bucket_width)
                        stats[owner_name][bucket][0] += 1
                        total_shots_all_robots += 1
                        nt = MovingTrack(b["x"], b["y"], power)
                        owner_tracks.append(nt)
                    moving_tracks[owner] = [t for t in owner_tracks if t.seen_this_tick]

                # --- Part 2: hits via energy-drop matching ---
                units = {str(u["i"]): u for u in d.get("u", [])}
                hitvictim_by_owner_power = defaultdict(list)
                for b in d.get("b", []):
                    if b.get("s") == "HIT_VICTIM":
                        hitvictim_by_owner_power[(b.get("o"), b.get("p", 0.0))].append(b)

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
                        expected = bullet_damage(power)
                        if abs(expected - drop) > ENERGY_TOLERANCE:
                            continue
                        for b in blist:
                            key = (owner, round(power, 2), round(b["x"], 0), round(b["y"], 0))
                            if key in seen_hit_keys:
                                continue
                            seen_hit_keys.add(key)
                            owner_name = robots.get(str(owner), str(owner))
                            bucket = bucket_power(power, bucket_width)
                            stats[owner_name][bucket][1] += 1
                            break
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
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("logdir", help="Path to a /logs/rounds/<N> directory")
    parser.add_argument("--bucket-width", type=float, default=0.5,
                         help="Width of power buckets (default 0.5)")
    args = parser.parse_args()
    sys.exit(analyze(args.logdir, args.bucket_width))
