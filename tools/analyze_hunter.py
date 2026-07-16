#!/usr/bin/env python3
"""Quick summary for current mcd8604__hunter Robocode traces.

Usage: python3 tools/analyze_hunter.py /logs/rounds/0
"""
import glob, json, math, os, statistics, sys
root = sys.argv[1] if len(sys.argv) > 1 else '/logs/rounds/0'
paths = sorted(glob.glob(os.path.join(root, 'sim_*.jsonl')))
if not paths:
    raise SystemExit(f'no sim traces under {root}')
win = loss = draw = 0
lengths = []; my_end = []; en_end = []; ranges = []; speeds = []; turns = []; walls = []; stops = []; fulls = []; edrops = []; mydrops = []
for p in paths:
    with open(p) as f:
        lines = f.read().splitlines()
    if not lines: continue
    meta = json.loads(lines[0]); ids = meta.get('robots', {})
    my = next((int(i) for i,n in ids.items() if 'gpt_5_5' in n), None)
    en = next((int(i) for i,n in ids.items() if 'mcd8604__hunter' in n), None)
    if my is None or en is None: continue
    prev_h = prev_e = prev_my_e = None; last = None; local_ranges = []
    for line in lines[1:]:
        r = json.loads(line); bd = {u['i']: u for u in r.get('u', [])}
        if my not in bd or en not in bd: continue
        m, e = bd[my], bd[en]; last = bd
        d = math.hypot(e['x'] - m['x'], e['y'] - m['y']); ranges.append(d); local_ranges.append(d)
        speeds.append(abs(e['v'])); stops.append(abs(e['v']) < .1); fulls.append(abs(e['v']) > 5)
        walls.append(e['x'] < 70 or e['x'] > 730 or e['y'] < 70 or e['y'] > 530)
        if prev_h is not None:
            turns.append(abs((e['bh'] - prev_h + math.pi) % (2 * math.pi) - math.pi))
        prev_h = e['bh']
        if prev_e is not None:
            drop = prev_e - e['e']
            if .09 < drop <= 3.01: edrops.append(drop)
        prev_e = e['e']
        if prev_my_e is not None:
            drop = prev_my_e - m['e']
            if .09 < drop <= 3.01: mydrops.append(drop)
        prev_my_e = m['e']
    if not last: continue
    lengths.append(len(lines) - 1); me = last[my]['e']; ee = last[en]['e']; my_end.append(me); en_end.append(ee)
    if me > 0 and ee <= 0: win += 1
    elif ee > 0 and me <= 0: loss += 1
    else: draw += 1

def q(arr, frac):
    if not arr: return None
    return sorted(arr)[min(len(arr)-1, max(0, int(frac * (len(arr)-1))))]
def show(name, arr):
    if arr:
        print(f'{name}: n={len(arr)} mean={statistics.mean(arr):.2f} med={statistics.median(arr):.2f} p10={q(arr,.1):.2f} p90={q(arr,.9):.2f} min={min(arr):.2f} max={max(arr):.2f}')
print(f'traces={len(paths)} live_outcome win/loss/draw-ish={win}/{loss}/{draw}')
for name, arr in [('length', lengths), ('my_end_energy', my_end), ('enemy_end_energy', en_end), ('range', ranges), ('enemy_speed', speeds), ('enemy_turn', turns), ('enemy_energy_drops', edrops), ('our_energy_drops', mydrops)]:
    show(name, arr)
if ranges:
    print(f'wallfrac={sum(walls)/len(walls):.3f} stopfrac={sum(stops)/len(stops):.3f} fullspeedfrac={sum(fulls)/len(fulls):.3f} enemy_shots_per_game={len(edrops)/max(1,len(paths)):.2f}')
