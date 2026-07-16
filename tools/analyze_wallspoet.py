#!/usr/bin/env python3
import glob,json,math,sys,statistics
pat=sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/1/sim_*.jsonl'
rows=[]
for fn in sorted(glob.glob(pat)):
    with open(fn) as f:
        header=json.loads(next(f)); names=header['robots']; my=[k for k,v in names.items() if 'gpt' in v][0]; en=[k for k in names if k!=my][0]
        prev=None; drops=[]; myshots=[]; enshots=[]; dists=[]; wall=stop=straight=live=0; end=None; mind=999; minme=999; bullet_hits=[]
        for line in f:
            o=json.loads(line); us={str(u['i']):u for u in o.get('u',[])}
            if my in us and en in us:
                m=us[my]; e=us[en]; end=(m,e,o['t']); live+=1
                dx=e['x']-m['x']; dy=e['y']-m['y']; d=math.hypot(dx,dy); dists.append(d); mind=min(mind,d); minme=min(minme,m['e'])
                if e['x']<70 or e['x']>730 or e['y']<70 or e['y']>530: wall+=1
                if abs(e['v'])<0.15: stop+=1
                # drops
                if prev:
                    pm,pe=prev
                    de=pe['e']-e['e']; dm=pm['e']-m['e']
                    if 0.09<de<=3.01: drops.append((o['t'],de,d,m['e'],e['e']))
                    if 0.09<dm<=3.01: pass
                prev=(m,e)
            # bullet owners? b list likely x y owner power? collect new ids? skip
        if end:
            m,e,t=end
            win='my' if m['e']>0 and e['e']<=0 else ('en' if e['e']>0 and m['e']<=0 else ('draw' if m['e']<=0 and e['e']<=0 else 'unk'))
            rows.append(dict(fn=fn.split('/')[-1],win=win,t=t,myE=m['e'],enE=e['e'],minme=minme,mind=mind,avgd=statistics.mean(dists),drops=len(drops),avgdrop=statistics.mean([x[1] for x in drops]) if drops else 0, wall=wall/live, stop=stop/live, firstdrop=drops[0][0] if drops else None))
for grp in ['my','en','draw']:
    rs=[r for r in rows if r['win']==grp]
    if not rs: continue
    print('\n',grp,len(rs))
    for key in ['t','myE','enE','minme','mind','avgd','drops','avgdrop','wall','stop','firstdrop']:
        vals=[r[key] for r in rs if r[key] is not None]
        if vals: print(key, round(statistics.mean(vals),2), 'med', round(statistics.median(vals),2), 'min', round(min(vals),2), 'max', round(max(vals),2))
print('\nloss examples')
for r in sorted([r for r in rows if r['win']!='my'], key=lambda x:x['t'], reverse=True)[:20]: print(r)
