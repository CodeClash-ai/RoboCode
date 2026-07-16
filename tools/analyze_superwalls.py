#!/usr/bin/env python3
"""Quick summary for the current mgalushka__superwalls matchup logs."""
import sys, glob, json, math, os, statistics
paths=[]
for a in sys.argv[1:] or ['/logs/rounds/1']:
    paths += glob.glob(os.path.join(a,'sim_*.jsonl')) if os.path.isdir(a) else glob.glob(a)
paths=sorted(paths, key=lambda p:int(os.path.basename(p).split('_')[1].split('.')[0]) if '_' in os.path.basename(p) else 0)
rows=[]
for p in paths:
    with open(p, errors='replace') as f:
        meta=json.loads(next(f)); names=meta['robots']; w=meta.get('w',800); h=meta.get('h',600)
        mid=[int(k) for k,v in names.items() if 'gpt' in v][0]; oid=[int(k) for k in names if int(k)!=mid][0]
        prev={}; mdrops=[]; odrops=[]; dists=[]; owalls=ostop=ofast=close=0; ospeeds=[]; myE=[]; opE=[]; end={}
        for line in f:
            rec=json.loads(line); units={u['i']:u for u in rec.get('u',[])}
            if mid in units and oid in units:
                m=units[mid]; o=units[oid]; d=math.hypot(m['x']-o['x'], m['y']-o['y']); dists.append(d)
                close += d < 250
                owalls += min(o['x'], w-o['x'], o['y'], h-o['y']) < 70
                ostop += abs(o['v']) < .2; ofast += abs(o['v']) > 7.5; ospeeds.append(abs(o['v']))
                myE.append(m['e']); opE.append(o['e'])
                for i,u,arr in [(mid,m,mdrops),(oid,o,odrops)]:
                    if i in prev:
                        de=prev[i]-u['e']
                        if .09 < de <= 3.01: arr.append(de)
                    prev[i]=u['e']
                end={mid:m, oid:o}
    if not end: continue
    if end[mid]['s']=='DEAD' and end[oid]['s']!='DEAD': out='loss'
    elif end[oid]['s']=='DEAD' and end[mid]['s']!='DEAD': out='win'
    elif myE[-1] > opE[-1] + .1: out='win'
    elif opE[-1] > myE[-1] + .1: out='loss'
    else: out='draw'
    n=max(1,len(dists))
    rows.append(dict(path=p,out=out,t=n,avgd=statistics.mean(dists),mind=min(dists),close=close/n,owall=owalls/n,
        ostop=ostop/n,ofast=ofast/n,ospeed=statistics.mean(ospeeds),myshots=len(mdrops),mypavg=statistics.mean(mdrops) if mdrops else 0,
        oshots=len(odrops),opavg=statistics.mean(odrops) if odrops else 0,myend=myE[-1],opend=opE[-1]))
print('n',len(rows),{k:sum(r['out']==k for r in rows) for k in ['win','loss','draw']})
for key in ['avgd','mind','close','owall','ostop','ofast','ospeed','myshots','mypavg','oshots','opavg','myend','opend']:
    parts=[]
    for out in ['win','loss','draw']:
        vals=[r[key] for r in rows if r['out']==out]
        if vals: parts.append(f"{out}={statistics.mean(vals):.3g} med {statistics.median(vals):.3g} n {len(vals)}")
    print(key, ' | '.join(parts))
print('worst losses')
for r in sorted([r for r in rows if r['out']=='loss'], key=lambda x:(x['opend'], x['mind']))[:20]:
    print(os.path.basename(r['path']), r)
