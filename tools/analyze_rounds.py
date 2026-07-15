#!/usr/bin/env python3
"""Summarize Robocode match logs from /logs/rounds.

Useful for future agents: prints aggregate winners/scores, the robots seen in
per-match result files, and basic trace facts such as whether opponents moved or
fired bullets in sim_*.jsonl logs.
"""
import glob, json, os, re
from collections import Counter, defaultdict

base = '/logs/rounds'
print('== results.json ==')
for path in sorted(glob.glob(base + '/*/results.json')):
    data = json.load(open(path))
    print(f"{path}: winner={data.get('winner')} scores={data.get('scores')}")

print('\n== per battle result files ==')
firsts = Counter()
robots = Counter()
for path in sorted(glob.glob(base + '/*/results_*.txt')):
    lines = [l for l in open(path, errors='replace') if l.strip()]
    for line in lines:
        m = re.match(r'\s*(\d)(?:st|nd|rd|th):\s+([^\s]+)\s+([0-9]+)', line)
        if m:
            place, name, score = m.groups()
            robots[name] += 1
            if place == '1':
                firsts[name] += 1
print('first-place counts:', dict(firsts))
print('robots seen:', dict(robots))

print('\n== sim trace sample ==')
# Inspect a limited number: enough to identify opponent behavior without spam.
for path in sorted(glob.glob(base + '/*/sim_*.jsonl'))[:25]:
    with open(path, errors='replace') as f:
        header = json.loads(next(f))
        stats = defaultdict(lambda: {'n':0, 'minx':1e9, 'maxx':-1e9, 'miny':1e9, 'maxy':-1e9, 'maxv':0.0, 'shots':0})
        last_energy = {}
        bullet_ticks = 0
        for line in f:
            rec = json.loads(line)
            if rec.get('b'):
                bullet_ticks += 1
            for u in rec.get('u', []):
                s = stats[u['i']]
                s['n'] += 1
                s['minx'] = min(s['minx'], u['x']); s['maxx'] = max(s['maxx'], u['x'])
                s['miny'] = min(s['miny'], u['y']); s['maxy'] = max(s['maxy'], u['y'])
                s['maxv'] = max(s['maxv'], abs(u.get('v', 0.0)))
                prev = last_energy.get(u['i'])
                if prev is not None and 0.09 < prev - u['e'] <= 3.01:
                    s['shots'] += 1
                last_energy[u['i']] = u['e']
    names = header.get('robots', {})
    print(os.path.basename(path), names)
    for i, s in sorted(stats.items()):
        print(f"  id={i} name={names.get(str(i), '?')} xspan={s['maxx']-s['minx']:.1f} yspan={s['maxy']-s['miny']:.1f} maxv={s['maxv']:.1f} energyDrops~shots={s['shots']}")
