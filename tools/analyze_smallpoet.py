#!/usr/bin/env python3
import json, glob, math, sys, pathlib, statistics as st, collections
rounddir=sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/1'
files=sorted(glob.glob(rounddir+'/sim_*.jsonl'))
rows=[]
for f in files:
    with open(f) as fh:
        header=json.loads(next(fh)); names=header['robots']
        my=[int(k) for k,v in names.items() if 'gpt' in v][0]
        en=[int(k) for k,v in names.items() if k!=str(my)][0]
        prev=None; dists=[]; myes=[]; enes=[]; mydrops=[]; endrops=[]; enstop=0; enwall=0; live=0; myshots=[]; enshots=[]
        winner=None; maxt=0
        for line in fh:
            o=json.loads(line)
            if 'winner' in o: winner=o.get('winner'); break
            t=o['t']; maxt=t
            us={u['i']:u for u in o['u']}
            if my not in us or en not in us: continue
            m,e=us[my],us[en]
            if m['s']=='ACTIVE' and e['s']=='ACTIVE':
                live+=1
                d=math.hypot(m['x']-e['x'],m['y']-e['y']); dists.append(d)
                myes.append(m['e']); enes.append(e['e'])
                if abs(e['v'])<0.1: enstop+=1
                if e['x']<70 or e['x']>730 or e['y']<70 or e['y']>530: enwall+=1
                if prev:
                    pm,pe=prev
                    dm=pm['e']-m['e']; de=pe['e']-e['e']
                    if 0.09<dm<=3.05: mydrops.append(dm)
                    if 0.09<de<=3.05: endrops.append(de)
            prev=(m,e)
        rows.append(dict(file=pathlib.Path(f).name,w=winner,t=maxt,live=live,dist=st.mean(dists) if dists else 0,mind=min(dists) if dists else 0,
                         myend=myes[-1] if myes else 0,enend=enes[-1] if enes else 0,mymin=min(myes) if myes else 0,
                         enstop=enstop/live if live else 0,enwall=enwall/live if live else 0,
                         myshots=len(mydrops), myp=st.mean(mydrops) if mydrops else 0, enshots=len(endrops), enp=st.mean(endrops) if endrops else 0))
for key in ['gpt_5_5','pez__smallpoet','TIE']:
    grp=[r for r in rows if r['w']==key]
    if not grp: continue
    print('\n',key,len(grp))
    for fld in ['t','dist','mind','myend','enend','mymin','enstop','enwall','myshots','myp','enshots','enp']:
        vals=[r[fld] for r in grp]
        print(f'{fld}: mean {st.mean(vals):.2f} med {st.median(vals):.2f} min {min(vals):.2f} max {max(vals):.2f}')
print('\nLoss/tie rows:')
for r in rows:
    if r['w']!='gpt_5_5': print(r)
