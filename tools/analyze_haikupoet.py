#!/usr/bin/env python3
"""Quick summary for pez__haikupoet trace logs.
Usage: tools/analyze_haikupoet.py /logs/rounds/1 or '/logs/rounds/1/sim_*.jsonl'
"""
import glob, json, math, os, statistics, sys
pat = sys.argv[1] if len(sys.argv) > 1 else '/logs/rounds/1'
if os.path.isdir(pat): pat = os.path.join(pat, 'sim_*.jsonl')
rows=[]
for f in glob.glob(pat):
    with open(f) as fh:
        header=json.loads(next(fh)); robots=header['robots']
        us=[int(k) for k,v in robots.items() if 'gpt' in v][0]
        en=[int(k) for k in robots if int(k)!=us][0]
        prev={}; mind=999; dists=[]; usdrops=[]; endu=ende=0; winner=''; draw=False; length=0
        for line in fh:
            d=json.loads(line)
            if 'winner' in d:
                winner=d.get('winner') or ''; draw=d.get('draw', False); continue
            length=d.get('t', length); cur={u['i']:u for u in d.get('u',[])}
            if us in cur and en in cur:
                u,e=cur[us],cur[en]; endu=u['e']; ende=e['e']
                dist=math.hypot(u['x']-e['x'], u['y']-e['y']); dists.append(dist); mind=min(mind, dist)
                if us in prev:
                    drop=prev[us]['e']-u['e']
                    if 0.09 < drop <= 3.01: usdrops.append(drop)
            prev=cur
        res='draw' if draw else ('us' if 'gpt' in winner else 'enemy')
        rows.append((res, os.path.basename(f), length, endu, ende, mind, statistics.mean(dists) if dists else 0, len(usdrops), statistics.mean(usdrops) if usdrops else 0))
print('count', len(rows), {r:sum(1 for x in rows if x[0]==r) for r in ['us','enemy','draw']})
for r in ['us','enemy','draw']:
    rr=[x for x in rows if x[0]==r]
    if rr:
        print(r, 'n',len(rr),'len',round(statistics.mean(x[2] for x in rr),1),'endE',round(statistics.mean(x[3] for x in rr),1),round(statistics.mean(x[4] for x in rr),1),'minD',round(statistics.mean(x[5] for x in rr),1),'avgD',round(statistics.mean(x[6] for x in rr),1),'shots',round(statistics.mean(x[7] for x in rr),1),'p',round(statistics.mean(x[8] for x in rr),2))
print('worst non-wins:')
for x in [x for x in rows if x[0] != 'us'][:12]: print(x)
