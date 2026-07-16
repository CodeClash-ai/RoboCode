#!/usr/bin/env python3
"""Quick summaries for logancsc__dodgebot2 Robocode jsonl traces.
Usage: tools/analyze_dodgebot2.py /logs/rounds/1  (or a glob of sim_*.jsonl)
"""
import glob, json, math, os, statistics, sys
arg = sys.argv[1] if len(sys.argv) > 1 else '/logs/rounds/1'
files = glob.glob(os.path.join(arg, 'sim_*.jsonl')) if os.path.isdir(arg) else glob.glob(arg)
files = sorted(files, key=lambda p: int(os.path.basename(p).split('_')[1].split('.')[0]))
by = {'W': [], 'L': [], 'D': [], 'A': []}
for f in files:
    frames=[]; mydrops=[]; opdrops=[]; prev=None
    for line in open(f):
        o=json.loads(line)
        if 'u' not in o: continue
        us={u['i']:u for u in o['u']}
        if 0 not in us or 1 not in us: continue
        me,op=us[0],us[1]
        d=math.hypot(me['x']-op['x'], me['y']-op['y'])
        frames.append((o['t'], me['e'], op['e'], d, me['x'], me['y'], op['x'], op['y']))
        if prev:
            pm,po=prev
            dm,do=pm['e']-me['e'], po['e']-op['e']
            if 0.09 <= dm <= 3.05: mydrops.append(dm)
            if 0.09 <= do <= 3.05: opdrops.append(do)
        prev=(me,op)
    if not frames: continue
    meE,opE=frames[-1][1],frames[-1][2]
    out = 'W' if meE>0 and opE<=0 else 'L' if opE>0 and meE<=0 else 'D' if meE<=0 and opE<=0 else 'A'
    dists=[x[3] for x in frames]
    alive=[x for x in frames if x[1]>0]
    by[out].append(dict(file=os.path.basename(f), t=frames[-1][0], me=meE, op=opE,
        avgd=statistics.mean(dists), mind=min(dists), close=sum(d<180 for d in dists)/len(dists),
        last100=statistics.mean([x[3] for x in alive[-100:]]) if alive else 0,
        myshots=len(mydrops), oppshots=len(opdrops), myavg=statistics.mean(mydrops) if mydrops else 0,
        opavg=statistics.mean(opdrops) if opdrops else 0))
print('files', len(files), 'outcomes', {k:len(v) for k,v in by.items()})
for k, rows in by.items():
    if not rows: continue
    print('\n', k, 'count', len(rows))
    for field in ['t','me','op','avgd','mind','close','last100','myshots','oppshots','myavg','opavg']:
        print(' ', field, round(statistics.mean(r[field] for r in rows), 3))
    print(' examples', rows[:10])
