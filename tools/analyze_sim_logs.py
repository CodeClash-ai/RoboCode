#!/usr/bin/env python3
"""
Analyze CodeClash RoboCode `sim_*.jsonl` match logs (found under /logs/rounds/<N>/).

Usage:
    python3 tools/analyze_sim_logs.py /logs/rounds/1

For each sim_*.jsonl file in the given directory, prints:
  - how many distinct robot ids/names appear in the "robots" header
  - whether the tracked robot(s) ever move (distinct x/y/velocity values)
  - whether any bullets ("b") ever appear
  - the final winner line

This was written after noticing that round 0 and round 1 sim logs both only
ever contain ONE robot ("sonnet_5") with zero movement, zero shots, and zero
bullets for the entire game -- i.e. the opponent never loaded into the match
at all, and our own bot also never left its spawn point (which makes sense:
our bot's movement/firing logic all lives inside onScannedRobot(), which never
fires if there's no other robot to scan). We were winning by walkover, not by
actually outplaying the opponent's code.

If in some future round the opponent bot *does* show up (robots dict will
have 2+ entries, and you'll see "b" bullets and moving x/y), this script will
flag that clearly so you know real combat data is available to tune against.
"""
import json
import sys
import glob
import os


def analyze_file(path):
    robots = {}
    xs = {}
    ys = {}
    vs = {}
    bullets_seen = 0
    winner = None
    n_turns = 0
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            d = json.loads(line)
            if "robots" in d:
                robots = d["robots"]
                for rid in robots:
                    xs[rid] = set()
                    ys[rid] = set()
                    vs[rid] = set()
                continue
            if "winner" in d:
                winner = d["winner"]
                continue
            if "u" in d:
                n_turns += 1
                for u in d["u"]:
                    rid = str(u["i"])
                    xs.setdefault(rid, set()).add(u["x"])
                    ys.setdefault(rid, set()).add(u["y"])
                    vs.setdefault(rid, set()).add(u["v"])
                if d.get("b"):
                    bullets_seen += len(d["b"])
    return {
        "path": path,
        "robots": robots,
        "n_turns": n_turns,
        "winner": winner,
        "bullets_seen": bullets_seen,
        "moved": {rid: (len(xs.get(rid, [])) > 1 or len(ys.get(rid, [])) > 1) for rid in robots},
    }


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    d = sys.argv[1]
    files = sorted(glob.glob(os.path.join(d, "sim_*.jsonl")))
    if not files:
        print(f"No sim_*.jsonl files found in {d}")
        sys.exit(1)

    n_single_robot = 0
    n_multi_robot = 0
    n_any_bullets = 0
    n_any_movement = 0

    for path in files:
        info = analyze_file(path)
        if len(info["robots"]) <= 1:
            n_single_robot += 1
        else:
            n_multi_robot += 1
        if info["bullets_seen"] > 0:
            n_any_bullets += 1
        if any(info["moved"].values()):
            n_any_movement += 1

    print(f"Analyzed {len(files)} sim files in {d}")
    print(f"  Games with only 1 robot present (likely opponent walkover): {n_single_robot}")
    print(f"  Games with 2+ robots present:                               {n_multi_robot}")
    print(f"  Games with any bullets fired:                               {n_any_bullets}")
    print(f"  Games with any robot movement at all:                       {n_any_movement}")

    # Show one example in detail
    example = analyze_file(files[0])
    print("\nExample (first file):")
    print(json.dumps(example, indent=2, default=str))


if __name__ == "__main__":
    main()
