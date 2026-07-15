#!/usr/bin/env python3
"""
analyze_ram_swing.py -- measure per-robot energy loss from robot-robot
CONTACT specifically (as opposed to bullet damage), from sim_*.jsonl logs.

Motivation / history (see README_agent.md rounds 102, 130, 131, 132, 133 for
the full story): rounds 130/131 originally tried to measure "how much energy
does each robot lose from ramming" by summing energy deltas ONLY on ticks
where THAT SAME robot's own logged status field read "HIT_ROBOT". This
looked like a real ~2x disadvantage for us against `logancsc__dodgebot2`,
motivating a (later-reverted) ramming-behavior change in round 131.

Round 133 found this was a MEASUREMENT BUG, not a real effect: Robocode's
per-tick log only reports status="HIT_ROBOT" for ONE side of a mutual
collision in a given tick -- the OTHER side's energy still visibly drops by
the same amount that tick, but its own status field stays "ACTIVE". Filtering
by "my own status" therefore systematically UNDER-counts one side's real
contact losses (whichever side the log's status field didn't happen to
flag that tick), manufacturing a fake asymmetry that isn't really there.

THE CORRECT METHOD (implemented here): treat a tick as a "contact tick" if
EITHER robot's status shows HIT_ROBOT that tick, then sum BOTH robots' own
raw energy deltas for that tick (not just the one whose status flag was
set). This is the exact method round 133 used to find the true, corrected
numbers (and which fully explained why round 131's "fix" for the fake
asymmetry barely changed anything real in round 132's follow-up match).

Caveat (round 102): a HIT_ROBOT-status tick can sometimes ALSO coincide with
a bullet impact landing in the exact same tick (both events triggering in
one frame). A bullet hit's damage is a big, clean chunk matching
Rules.getBulletDamage() (e.g. -16.0 for a power-3 hit), while pure contact
damage is always a small, flat -0.6 (ROBOT_HIT_DAMAGE) or -1.8
(ROBOT_HIT_DAMAGE + ROBOT_HIT_BONUS, if this is the "rammed" side of a
fresh moving-into collision) per tick. To avoid attributing bullet damage to
ramming, this script only counts a tick's delta toward the "contact swing"
total if the delta's magnitude is < ENERGY_DELTA_CAP (default 5.0 energy) --
comfortably above the largest plausible pure-contact delta (a robot touching
2 other robots in the same tick could see -3.6, still well under 5) while
excluding essentially all real bullet-damage deltas (min real bullet damage
at P=0.1 is 0.4, but that's rare; the important cutoff is excluding the
common 6-18 range of P=1-3 hits).

Usage:
    python3 tools/analyze_ram_swing.py /logs/rounds/<N> [--energy-delta-cap 5.0]

Prints, per robot: total contact-tick energy lost (summed across all games),
per-game average, and the number of contact ticks counted.
"""
import argparse
import glob
import json
import os
import sys
from collections import defaultdict


def analyze_dir(log_dir, energy_delta_cap):
    total_loss = defaultdict(float)
    total_ticks = defaultdict(int)
    n_games = 0

    for path in sorted(glob.glob(os.path.join(log_dir, "sim_*.jsonl"))):
        with open(path) as f:
            lines = f.readlines()
        if not lines:
            continue
        header = json.loads(lines[0])
        robots = header.get("robots", {})
        if not robots:
            continue
        n_games += 1

        last_energy = {}  # idx (str) -> last seen energy
        for line in lines[1:]:
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue
            updates = rec.get("u", [])
            if not updates:
                continue

            # Determine if this tick is a "contact tick" for ANY robot.
            is_contact_tick = any(u.get("s") == "HIT_ROBOT" for u in updates)

            for u in updates:
                idx = str(u.get("i"))
                e = u.get("e")
                if e is None:
                    continue
                prev = last_energy.get(idx)
                if prev is not None and is_contact_tick:
                    delta = e - prev
                    if delta < 0 and abs(delta) < energy_delta_cap:
                        name = robots.get(idx, idx)
                        total_loss[name] += delta
                        total_ticks[name] += 1
                last_energy[idx] = e

    return total_loss, total_ticks, n_games


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("log_dir")
    ap.add_argument("--energy-delta-cap", type=float, default=5.0)
    args = ap.parse_args()

    total_loss, total_ticks, n_games = analyze_dir(args.log_dir, args.energy_delta_cap)
    if n_games == 0:
        print("No games found in", args.log_dir)
        sys.exit(1)

    print(f"Analyzed {n_games} games in {args.log_dir} "
          f"(energy-delta-cap={args.energy_delta_cap})")
    print("Corrected contact-tick net energy loss (either robot's status=="
          "HIT_ROBOT counts as a contact tick; both robots' own deltas summed):")
    for name in sorted(total_loss.keys()):
        loss = total_loss[name]
        ticks = total_ticks[name]
        print(f"  {name:30s}: total {loss:9.1f}  |  per-game {loss / n_games:7.2f}  "
              f"|  contact-ticks {ticks}")


if __name__ == "__main__":
    main()
