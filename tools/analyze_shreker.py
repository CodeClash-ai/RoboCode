#!/usr/bin/env python3
import json, glob, math, statistics, sys
paths=[]
for pat in sys.argv[1:] or ['/logs/rounds/1/sim_*.jsonl']:
    paths += glob.glob(pat)
# robocode bullet damage: p<=1 ->4p, else 4p+2(p-1)
def dmg(p): return 4*p if p<=1 else 4*p+2*(p-1)
rows=[]
for fn in sorted(paths):
    me=opp=None; prevE={}; myshots=[]; oppshots=[]; myhits=[]; opphits=[]; final=None; last={}; mypos=[]; opppos=[]; dist=[]
    # bullets: id not present, infer hits by energy increases? simpler: collect drops and end states
    for line in open(fn):
        o=json.loads(line)
        if 'robots' in o:
            for i,n in o['robots'].items():
                if 'gpt' in n: me=int(i)
                else: opp=int(i)
            continue
        if 'winner' in o:
            final=o; continue
        for u in o['u']:
            i=u['i']; last[i]=u
        if me in last and opp in last:
            dist.append(math.hypot(last[me]['x']-last[opp]['x'], last[me]['y']-last[opp]['y']))
        for u in o['u']:
            i=u['i']; e=u['e']
            if i in prevE:
                drop=prevE[i]-e
                if 0.09<drop<=3.01:
                    if i==me: myshots.append((o['t'],drop,e,last.get(opp,{}).get('e'), dist[-1] if dist else None))
                    elif i==opp: oppshots.append((o['t'],drop,e,last.get(me,{}).get('e'), dist[-1] if dist else None))
            prevE[i]=e
    if not final: continue
    outcome='draw' if final.get('draw') else ('win' if 'gpt' in final.get('winner','') else 'loss')
    rows.append((outcome, fn, last[me]['e'], last[opp]['e'], len(myshots), len(oppshots), statistics.mean([x[1] for x in myshots]) if myshots else 0, statistics.mean([x[1] for x in oppshots]) if oppshots else 0, len(dist), statistics.mean(dist) if dist else 0, myshots, oppshots))
from collections import defaultdict
by=defaultdict(list)
for r in rows: by[r[0]].append(r)
for k,rs in by.items():
    print('\n',k,len(rs))
    for idx,name in [(2,'myEnd'),(3,'oppEnd'),(4,'myshots'),(5,'oppshots'),(6,'mypow'),(7,'opppow'),(8,'len'),(9,'dist')]:
        print(name, statistics.mean([r[idx] for r in rs]), 'med', statistics.median([r[idx] for r in rs]))
    # shot power by energy tier
    allshots=[s for r in rs for s in r[10]]
    for lo,hi in [(0,10),(10,18),(18,38),(38,62),(62,200)]:
        xs=[s for s in allshots if lo<=s[2]<hi]
        if xs: print(' my shot while E',lo,hi,'n',len(xs),'pavg',statistics.mean(x[1] for x in xs),'dist',statistics.mean(x[4] for x in xs if x[4]))
print('\nloss files')
for r in by['loss'][:20]: print(r[1], 'myE oppE shots', r[2],r[3],r[4],r[5], 'last myshots', r[10][-5:])
