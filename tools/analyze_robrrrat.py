#!/usr/bin/env python3
"""Quick trace summary for sacdalance__robrrrat Robocode logs.
Usage: tools/analyze_robrrrat.py /logs/rounds/1
"""
import glob, json, math, os, statistics, sys
base = sys.argv[1] if len(sys.argv) > 1 else '/logs/rounds/1'
rows = []
for fn in sorted(glob.glob(os.path.join(base, 'sim_*.jsonl'))):
    us = op = None; prev = last = None; t = 0
    dists = []; our_drops = []; opp_drops = []
    min_us_energy = 999.0
    for line in open(fn):
        o = json.loads(line)
        if 'robots' in o:
            for k, v in o['robots'].items():
                if 'gpt' in v: us = int(k)
                else: op = int(k)
            continue
        if 't' not in o or 'u' not in o: continue
        t = o['t']; units = {u['i']: u for u in o['u']}; last = units
        if us in units:
            min_us_energy = min(min_us_energy, units[us]['e'])
        if us in units and op in units and units[us]['s'] == 'ACTIVE' and units[op]['s'] == 'ACTIVE':
            dists.append(math.hypot(units[us]['x']-units[op]['x'], units[us]['y']-units[op]['y']))
        if prev and us in units and op in units and us in prev and op in prev:
            du = prev[us]['e'] - units[us]['e']
            do = prev[op]['e'] - units[op]['e']
            if 0.09 <= du <= 3.01: our_drops.append(du)
            if 0.09 <= do <= 3.01: opp_drops.append(do)
        prev = units
    if not last: continue
    ue = last.get(us, {}).get('e', 0.0); oe = last.get(op, {}).get('e', 0.0)
    winner = 'us' if ue > 0 and oe <= 0 else ('op' if oe > 0 and ue <= 0 else 'draw')
    rows.append((winner, t, ue, oe, min_us_energy, len(our_drops), statistics.mean(our_drops) if our_drops else 0.0,
                 len(opp_drops), statistics.mean(opp_drops) if opp_drops else 0.0, statistics.mean(dists) if dists else 0.0,
                 min(dists) if dists else 0.0, os.path.basename(fn)))
print('files', len(rows), 'base', base)
for winner in ['us', 'op', 'draw']:
    sub = [r for r in rows if r[0] == winner]
    if not sub: continue
    print(winner, 'n', len(sub),
          'avg_len', round(statistics.mean(r[1] for r in sub),1),
          'avg_us_end', round(statistics.mean(r[2] for r in sub),1),
          'avg_op_end', round(statistics.mean(r[3] for r in sub),1),
          'avg_min_us', round(statistics.mean(r[4] for r in sub),1),
          'our_shots', round(statistics.mean(r[5] for r in sub),1),
          'our_p', round(statistics.mean(r[6] for r in sub),2),
          'opp_drops', round(statistics.mean(r[7] for r in sub),1),
          'opp_p', round(statistics.mean(r[8] for r in sub),2),
          'dist', round(statistics.mean(r[9] for r in sub),1),
          'mindist', round(statistics.mean(r[10] for r in sub),1))
print('op/draw examples:')
for r in [r for r in rows if r[0] != 'us'][:20]:
    print(r[-1], r[:-1])
