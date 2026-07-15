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

Round 26 addition: also filters out a THIRD known false-positive pattern
(distinct from the pre-existing "robot X itself is DEAD" exclusion): once
every OTHER robot in the game is already DEAD, the match is effectively
decided, and the log keeps appending trailing frames for the survivor with
byte-identical x/y/rh (a rendering/logging tail, not a real gameplay freeze
-- confirmed by manually tracing several round-25/26 findings that all
turned out to have the sole opponent showing status=DEAD for the entire
flagged range, while the "frozen" survivor's own status stayed ACTIVE the
whole time and the freeze always ended exactly at the last tick of the
file). These are now labeled POST-VICTORY-TAIL and excluded from the
default findings list (pass --show-post-victory to include them anyway if
you want to double check this filtering isn't hiding something real).
"""
import json
import sys
import glob
import os
import argparse


def analyze_file(path, threshold, show_post_victory=False):
    findings = []
    robots = {}
    # streak tracking per robot id: (last_x, last_y, pos_streak, pos_start_t,
    #                                 last_rh, rh_streak, rh_start_t)
    state = {}
    max_t = 0
    # Round 26: also record, per tick, the set of robot ids that are ACTIVE
    # (not DEAD) at that tick, so we can later check whether a flagged robot
    # was the ONLY survivor for the whole duration of its freeze streak (see
    # POST-VICTORY-TAIL note in the module docstring above).
    active_at_tick = {}  # t -> set of robot ids with status != DEAD at that t
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
            tick_active = set()
            for u in rec.get("u", []):
                i = str(u["i"])
                x, y, rh = u.get("x"), u.get("y"), u.get("rh")
                status = u.get("s")
                if status != "DEAD":
                    tick_active.add(i)
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
            active_at_tick[t] = tick_active

    def other_robots_all_dead_during(robot_id, start_t, end_t):
        """True if, for every tick in [start_t, end_t], no OTHER robot id is
        active (i.e. the match was already effectively decided/over for the
        whole flagged range)."""
        for t in range(start_t, end_t + 1):
            active = active_at_tick.get(t)
            if active is None:
                continue
            others = active - {robot_id}
            if others:
                return False
        return True

    for i, st in state.items():
        name = robots.get(i, i)
        # Skip freezes that end with the robot in DEAD status -- that's expected
        # (a dead robot's last known x/y/rh obviously stop changing) and not a
        # bug, so it would just be noise obscuring genuine in-life freezes.
        if st["max_pos_streak"] >= threshold and st["max_pos_streak_status"] != "DEAD":
            start_t, end_t = st["max_pos_range"]
            if other_robots_all_dead_during(i, start_t, end_t):
                if show_post_victory:
                    findings.append(
                        f"  robot {i} ({name}): POST-VICTORY-TAIL (position frozen, "
                        f"but every other robot was already dead) for "
                        f"{st['max_pos_streak']} ticks, range {st['max_pos_range']} "
                        f"(game had {max_t} total ticks)")
            # Round 15 addition: label this specific sub-case explicitly when the
            # freeze's terminal status is HIT_ROBOT -- this is the "stuck ramming"
            # mutual-collision-lock pattern documented in README_agent.md's round
            # 14 notes (onHitRobot()'s "press forward" logic getting wedged against
            # an enemy that itself can't fully separate, e.g. because it's wall-
            # stuck). Distinguishing this from a generic position freeze (which
            # historically meant the round-3 wall-standoff bug) makes it much
            # faster for a future teammate to tell which known failure class (if
            # any) a new finding matches, per round 14's suggested follow-up.
            elif st["max_pos_streak_status"] == "HIT_ROBOT":
                findings.append(
                    f"  robot {i} ({name}): STUCK-RAMMING (position frozen while "
                    f"status=HIT_ROBOT) for {st['max_pos_streak']} ticks, range "
                    f"{st['max_pos_range']} (game had {max_t} total ticks)")
            else:
                findings.append(
                    f"  robot {i} ({name}): position frozen for {st['max_pos_streak']} "
                    f"ticks, range {st['max_pos_range']} (game had {max_t} total ticks)")
        if st["max_rh_streak"] >= threshold and st["max_rh_streak_status"] != "DEAD":
            start_t, end_t = st["max_rh_range"]
            if other_robots_all_dead_during(i, start_t, end_t):
                if show_post_victory:
                    findings.append(
                        f"  robot {i} ({name}): POST-VICTORY-TAIL (radar heading "
                        f"frozen, but every other robot was already dead) for "
                        f"{st['max_rh_streak']} ticks, range {st['max_rh_range']} "
                        f"(game had {max_t} total ticks)")
            else:
                findings.append(
                    f"  robot {i} ({name}): radar heading frozen for {st['max_rh_streak']} "
                    f"ticks, range {st['max_rh_range']} (game had {max_t} total ticks)")
    return findings

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("logdir")
    ap.add_argument("--threshold", type=int, default=100)
    ap.add_argument("--show-post-victory", action="store_true",
                     help="Also show POST-VICTORY-TAIL findings (frozen position/"
                          "radar after every other robot is already dead) -- these "
                          "are excluded by default since round 26 confirmed they're "
                          "a harmless logging-tail artifact, not a real freeze bug.")
    args = ap.parse_args()

    files = sorted(glob.glob(os.path.join(args.logdir, "sim_*.jsonl")))
    total_findings = 0
    files_with_findings = 0
    for path in files:
        findings = analyze_file(path, args.threshold, show_post_victory=args.show_post_victory)
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
