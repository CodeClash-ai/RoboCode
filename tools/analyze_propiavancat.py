#!/usr/bin/env python3
"""Quick trace summary for pmontp19__propiavancat rounds."""
import glob, json, math, statistics, sys, os
pat = sys.argv[1] if len(sys.argv) > 1 else '/logs/rounds/0/sim_*.jsonl'
if os.path.isdir(pat): pat = os.path.join(pat, 'sim_*.jsonl')
files = sorted(glob.glob(pat))
out = {'us':0,'opp':0,'draw':0}; rows=[]
for f in files:
    ids=None; prevE={}; prevH={}; drops=[]; speeds=[]; turns=[]; dists=[]; wall=stop=live=0; end={}
    for line in open(f):
        o=json.loads(line)
        if 'robots' in o:
            ids={v:int(k) for k,v in o['robots'].items()}; continue
        if not ids or 'u' not in o: continue
        us=ids.get('gpt_5_5'); opp=ids.get('pmontp19__propiavancat')
        bs={b['i']:b for b in o['u']}
        if us in bs and opp in bs:
            bu,bo=bs[us],bs[opp]
            if bu['s']=='ACTIVE' and bo['s']=='ACTIVE':
                live+=1; speeds.append(abs(bo['v'])); dists.append(math.hypot(bu['x']-bo['x'],bu['y']-bo['y']))
                wall += min(bo['x'],800-bo['x'],bo['y'],600-bo['y']) < 70
                stop += abs(bo['v']) < .1
                if opp in prevH: turns.append(abs((bo['bh']-prevH[opp]+math.pi)%(2*math.pi)-math.pi))
                if opp in prevE:
                    de=prevE[opp]-bo['e']
                    if .09<de<=3.01: drops.append(de)
            for i,b in bs.items(): prevE[i]=b['e']; prevH[i]=b['bh']; end[i]=b['e']
    if end.get(us,0)>0 and end.get(opp,0)<=0: out['us']+=1
    elif end.get(opp,0)>0 and end.get(us,0)<=0: out['opp']+=1
    else: out['draw']+=1
    if live: rows.append((sum(speeds)/len(speeds), max(speeds), stop/live, wall/live, sum(turns)/len(turns or [1]), sum(dists)/len(dists), len(drops), sum(drops)/len(drops or [1]), end.get(us,0), end.get(opp,0), live, f))
print('files',len(files),'outcomes',out)
for idx,name in enumerate(['avgspd','maxspd','stopfrac','wallfrac','avgturn','avgdist','drops','dropavg','endus','endopp','live']):
    vals=[r[idx] for r in rows]; print(name, round(statistics.mean(vals),3), 'min', round(min(vals),3), 'max', round(max(vals),3))
print('losses')
for r in rows:
    if r[9]>0 and r[8]<=0: print(r[-1], tuple(round(x,2) if isinstance(x,float) else x for x in r[:-1]))
