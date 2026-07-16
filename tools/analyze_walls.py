#!/usr/bin/env python3
import json, glob, math, sys, os, statistics as st
path=sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/1'
files=sorted(glob.glob(path+'/sim_*.jsonl'), key=lambda p:int(os.path.basename(p).split('_')[1].split('.')[0]))
summary=[]
for f in files:
    names={}; me=opp=None; ticks=[]; winner=None
    with open(f) as fh:
        header=json.loads(next(fh)); names=header.get('robots',{})
        for k,v in names.items():
            if 'gpt' in v: me=int(k)
            else: opp=int(k)
        prev={}; mydrops=[]; oppdrops=[]; dists=[]; my_es=[]; opp_es=[]; close=0; bullets=[]; hitlike=0
        last_t=0
        for line in fh:
            rec=json.loads(line)
            if 'winner' in rec:
                winner=rec.get('winner'); break
            t=rec['t']; last_t=t
            units={u['i']:u for u in rec.get('u',[])}
            if me in units and opp in units:
                m=units[me]; o=units[opp]
                d=math.hypot(m['x']-o['x'], m['y']-o['y']); dists.append(d)
                my_es.append(m['e']); opp_es.append(o['e'])
                if d<250: close+=1
                if me in prev:
                    dm=prev[me]['e']-m['e']; do=prev[opp]['e']-o['e']
                    # fire drops approx 0.09-3.01 not bullet hit inflicted? energy may rise on bullet hit too.
                    if 0.09<dm<=3.01: mydrops.append(dm)
                    if 0.09<do<=3.01: oppdrops.append(do)
            prev=units
    w=winner or ''
    summary.append(dict(file=os.path.basename(f), winner=w, win=('gpt' in w), t=last_t, my_end=my_es[-1] if my_es else None, opp_end=opp_es[-1] if opp_es else None, my_min=min(my_es) if my_es else None, opp_min=min(opp_es) if opp_es else None, avgd=st.mean(dists) if dists else 0, mind=min(dists) if dists else 0, closefrac=close/len(dists) if dists else 0, myshots=len(mydrops), oppshots=len(oppdrops), myp=st.mean(mydrops) if mydrops else 0, oppp=st.mean(oppdrops) if oppdrops else 0))
for group,name in [(summary,'all'),([s for s in summary if s['win']],'wins'),([s for s in summary if not s['win']],'losses')]:
    if not group: continue
    print(name, 'n',len(group),'avg_t',st.mean(s['t'] for s in group),'avg_my_end',st.mean(s['my_end'] for s in group),'avg_opp_end',st.mean(s['opp_end'] for s in group),'avgd',st.mean(s['avgd'] for s in group),'mind',st.mean(s['mind'] for s in group),'myshots',st.mean(s['myshots'] for s in group),'oppdrops',st.mean(s['oppshots'] for s in group),'myp',st.mean(s['myp'] for s in group),'oppp',st.mean(s['oppp'] for s in group))
print('worst/ losses')
for s in sorted([s for s in summary if not s['win']], key=lambda x:x['t'], reverse=True)[:20]: print(s)
