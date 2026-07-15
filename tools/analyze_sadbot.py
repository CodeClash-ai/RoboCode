import json,glob,math,statistics,sys
pat=sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/1/sim_*.jsonl'
files=glob.glob(pat)
our='gpt_5_5'; enemy='avsthiago__sadbot'
agg=[]
for f in files:
    meta=None; prev={}; first={}; last={}; mins=[]; dists=[]; edrops=[]; odrops=[]; estops=estraight=ewall=0; live=0; close=0; ourfire=enemyfire=0; ourhits=enemyhits=0
    bullets_prev={}
    with open(f) as fh:
        for line in fh:
            o=json.loads(line)
            if 'robots' in o: meta=o; continue
            if 't' not in o or 'u' not in o: continue
            t=o['t']; units={u['i']:u for u in o['u']}
            ids={v:k for k,v in meta['robots'].items()}
            if our not in ids or enemy not in ids: continue
            oi=int(ids[our]); ei=int(ids[enemy])
            if oi not in units or ei not in units: continue
            ou,eu=units[oi],units[ei]
            if ou.get('s')!='ACTIVE' or eu.get('s')!='ACTIVE': continue
            live+=1; last=(ou,eu,t)
            dist=math.hypot(ou['x']-eu['x'],ou['y']-eu['y']); dists.append(dist)
            if dist<200: close+=1
            if abs(eu['v'])<.15: estops+=1
            # wall margin 70
            if eu['x']<70 or eu['x']>800-70 or eu['y']<70 or eu['y']>600-70: ewall+=1
            if oi in prev:
                po,pe=prev[oi],prev[ei]
                de=pe['e']-eu['e']; do=po['e']-ou['e']
                # drops without death? approximate fires include hit energy too ambiguous
                if 0.09<de<=3.01: edrops.append(de)
                if 0.09<do<=3.01: odrops.append(do)
            prev[oi]=ou; prev[ei]=eu
    if dists:
        agg.append(dict(file=f, live=live, avgdist=statistics.mean(dists), mindist=min(dists), closefrac=close/live, stopfrac=estops/live, wallfrac=ewall/live, edrops=len(edrops), avgdrop=statistics.mean(edrops) if edrops else 0, end_our=last[0]['e'], end_enemy=last[1]['e'], endt=last[2]))
print('n',len(agg))
for k in ['live','avgdist','mindist','closefrac','stopfrac','wallfrac','edrops','avgdrop','end_our','endt']:
    vals=[a[k] for a in agg]
    print(k, statistics.mean(vals), min(vals), max(vals))
print('longest')
for a in sorted(agg,key=lambda x:x['live'], reverse=True)[:10]: print(a)
