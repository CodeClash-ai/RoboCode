#!/usr/bin/env python3
import json, glob, math, statistics, collections, sys
pat = (sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/0')
files = glob.glob(pat if '*' in pat else pat.rstrip('/') + '/sim_*.jsonl')
rows=[]
for fn in files:
    my=en=None; last=None; dists=[]; speeds=[]; turns=[]; stops=walls=full=0; ef=[]; ms=[]; prevh=None; myend=enend=0
    for line in open(fn):
        o=json.loads(line)
        if 'robots' in o:
            for k,v in o['robots'].items():
                if 'gpt' in v: my=int(k)
                else: en=int(k)
            continue
        us={u['i']:u for u in o.get('u',[])}
        if my in us: myend=us[my]['e']
        if en in us: enend=us[en]['e']
        if my in us and en in us and us[my]['s']=='ACTIVE' and us[en]['s']=='ACTIVE':
            a,b=us[my],us[en]
            dists.append(math.hypot(a['x']-b['x'],a['y']-b['y']))
            speeds.append(abs(b['v']))
            stops += abs(b['v'])<0.5; full += abs(b['v'])>7.5
            walls += b['x']<70 or b['x']>730 or b['y']<70 or b['y']>530
            if prevh is not None: turns.append(abs((b['bh']-prevh+math.pi)%(2*math.pi)-math.pi))
            prevh=b['bh']
            if last:
                de=last[en]-b['e']; dm=last[my]-a['e']
                if 0.09<de<=3.01: ef.append(de)
                if 0.09<dm<=3.01: ms.append(dm)
            last={my:a['e'], en:b['e']}
    out='win' if myend>0 and enend<=0 else ('loss' if enend>0 and myend<=0 else 'draw')
    n=max(1,len(dists)); rows.append((out,len(dists),statistics.mean(dists or [0]),min(dists or [0]),statistics.mean(speeds or [0]),stops/n,walls/n,full/n,statistics.mean(turns or [0]),len(ef),statistics.mean(ef or [0]),len(ms),statistics.mean(ms or [0]),myend,enend,fn))
print(collections.Counter(r[0] for r in rows), 'files', len(rows))
for out in ['win','loss','draw']:
    sub=[r for r in rows if r[0]==out]
    if not sub: continue
    print(out, len(sub), 'ticks %.1f dist %.1f mind %.1f speed %.2f stop %.2f wall %.2f full %.2f turn %.3f efires %.1f epow %.2f myshots %.1f mypow %.2f myend %.1f enend %.1f' % tuple(statistics.mean(r[i] for r in sub) for i in [1,2,3,4,5,6,7,8,9,10,11,12,13,14]))
