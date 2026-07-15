#!/usr/bin/env python3
"""
Generalized freeze detector for CodeClash RoboCode `sim_*.jsonl` logs.

Detects two known failure classes (see README_agent.md history for how these
were found/fixed):
  1. "Wall/movement freeze": a robot's (x, y) stays byte-identical for many
     consecutive ticks while the round is still active.
  2. "Radar freeze": a robot's radar heading (rh) stays byte-identical for
     many consecutive ticks while the round is still active (which usually
     means onScannedRobot stops firing, so gun heading (gh) freezes too soon
     after).

Usage:
    python3 tools/analyze_freezes.py /logs/rounds/<N> [--threshold 100]

Prints, per sim file, any robot with a freeze streak (position or radar)
longer than the threshold (default 100 ticks), including the streak length
and the tick range it happened in. Silent (just a summary count) if no
freezes are found across all files -- that's the "healthy" case.
"""
import json
import sys
import glob
import os
import argparse


def analyze_file(path, threshold):
    findings = []
    robots = {}
    # streak tracking per robot id: (last_x, last_y, pos_streak, pos_start_t,
    #                                 last_rh, rh_streak, rh_start_t)
    state = {}
    max_t = 0
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rec = json.loads(line)
            if "robots" in rec:
                robots = rec["robots"]
                continue
            t = rec.get("t")
            if t is None:
                continue
            max_t = max(max_t, t)
            for u in rec.get("u", []):
                i = str(u["i"])
                x, y, rh = u.get("x"), u.get("y"), u.get("rh")
                status = u.get("s")
                st = state.setdefault(i, {
                    "last_x": None, "last_y": None, "pos_streak": 0, "pos_start": t,
                    "last_rh": None, "rh_streak": 0, "rh_start": t,
                    "max_pos_streak": 0, "max_pos_range": (t, t),
                    "max_rh_streak": 0, "max_rh_range": (t, t),
                    "max_pos_streak_status": None, "max_rh_streak_status": None,
                })
                if st["last_x"] == x and st["last_y"] == y:
                    st["pos_streak"] += 1
                else:
                    st["pos_streak"] = 0
                    st["pos_start"] = t
                    st["last_x"], st["last_y"] = x, y
                if st["pos_streak"] > st["max_pos_streak"]:
                    st["max_pos_streak"] = st["pos_streak"]
                    st["max_pos_range"] = (st["pos_start"], t)
                    st["max_pos_streak_status"] = status

                if st["last_rh"] == rh:
                    st["rh_streak"] += 1
                else:
                    st["rh_streak"] = 0
                    st["rh_start"] = t
                    st["last_rh"] = rh
                if st["rh_streak"] > st["max_rh_streak"]:
                    st["max_rh_streak"] = st["rh_streak"]
                    st["max_rh_range"] = (st["rh_start"], t)
                    st["max_rh_streak_status"] = status

    for i, st in state.items():
        name = robots.get(i, i)
        # Skip freezes that end with the robot in DEAD status -- that's expected
        # (a dead robot's last known x/y/rh obviously stop changing) and not a
        # bug, so it would just be noise obscuring genuine in-life freezes.
        if st["max_pos_streak"] >= threshold and st["max_pos_streak_status"] != "DEAD":
            findings.append(
                f"  robot {i} ({name}): position frozen for {st['max_pos_streak']} "
                f"ticks, range {st['max_pos_range']} (game had {max_t} total ticks)")
        if st["max_rh_streak"] >= threshold and st["max_rh_streak_status"] != "DEAD":
            findings.append(
                f"  robot {i} ({name}): radar heading frozen for {st['max_rh_streak']} "
                f"ticks, range {st['max_rh_range']} (game had {max_t} total ticks)")
    return findings


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("logdir")
    ap.add_argument("--threshold", type=int, default=100)
    args = ap.parse_args()

    files = sorted(glob.glob(os.path.join(args.logdir, "sim_*.jsonl")))
    total_findings = 0
    files_with_findings = 0
    for path in files:
        findings = analyze_file(path, args.threshold)
        if findings:
            files_with_findings += 1
            total_findings += len(findings)
            print(f"{os.path.basename(path)}:")
            for line in findings:
                print(line)

    print()
    print(f"Analyzed {len(files)} files in {args.logdir} (threshold={args.threshold} ticks)")
    print(f"Files with at least one freeze finding: {files_with_findings}")
    print(f"Total freeze findings: {total_findings}")
    if files_with_findings == 0:
        print("No freezes detected - looks healthy.")


if __name__ == "__main__":
    main()
