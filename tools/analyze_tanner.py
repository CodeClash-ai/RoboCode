#!/usr/bin/env python3
import glob,json,math,sys,os
base=sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/1/sim_*.jsonl'
rows=[]
for path in sorted(glob.glob(base)):
    with open(path) as f:
        header=json.loads(next(f)); names=header['robots']
        my=None; opp=None
        for k,v in names.items():
            if 'gpt' in v: my=int(k)
            else: opp=int(k)
        last={}; dists=[]; my_es=[]; opp_es=[]; opp_wall=opp_stop=opp_fast=opp_straight=0; n=0; opp_shots=[]; my_shots=[]; last_e={}; opp_turns=[]; last_h={}
        for line in f:
            rec=json.loads(line)
            if 't' not in rec:
                final=rec; continue
            t=rec['t']; units={u['i']:u for u in rec.get('u',[])}
            if my in units and opp in units:
                a=units[my]; b=units[opp]
                d=math.hypot(a['x']-b['x'],a['y']-b['y']); dists.append(d); my_es.append(a['e']); opp_es.append(b['e']); n+=1
                if b['x']<75 or b['x']>header['w']-75 or b['y']<75 or b['y']>header['h']-75: opp_wall+=1
                if abs(b.get('v',0))<.15: opp_stop+=1
                if abs(b.get('v',0))>5: opp_fast+=1
                if opp in last_h:
                    dh=abs((b['bh']-last_h[opp]+math.pi)%(2*math.pi)-math.pi); opp_turns.append(dh)
                    if abs(b.get('v',0))>.55 and dh<.012: opp_straight+=1
                last_h[opp]=b['bh']
            for i,u in units.items():
                prev=last_e.get(i)
                if prev is not None:
                    drop=prev-u['e']
                    if .09<drop<=3.01:
                        (my_shots if i==my else opp_shots).append((t,drop))
                last_e[i]=u['e']
            last=units
    me=last.get(my,{}); oe=last.get(opp,{})
    winner=final.get('winner','') if 'final' in locals() else ''
    winner = winner or ''
    if 'gpt' in winner: win='us'
    elif winner: win='opp'
    else: win='draw'
    final={}
    rows.append(dict(path=path,win=win,t=t,me=me.get('e',0),oe=oe.get('e',0),avgd=sum(dists)/len(dists) if dists else 0,mind=min(dists) if dists else 0,maxd=max(dists) if dists else 0,mymin=min(my_es) if my_es else 0,oppmin=min(opp_es) if opp_es else 0,oppwall=opp_wall/max(1,n),oppstop=opp_stop/max(1,n),oppfast=opp_fast/max(1,n),oppstraight=opp_straight/max(1,n),oppfire=len(opp_shots),oppfp=sum(x[1] for x in opp_shots)/len(opp_shots) if opp_shots else 0,myfire=len(my_shots),myfp=sum(x[1] for x in my_shots)/len(my_shots) if my_shots else 0))
from collections import Counter
print('count',Counter(r['win'] for r in rows),'n',len(rows))
for key in ['t','me','oe','avgd','mind','maxd','myfire','myfp','oppfire','oppfp','oppwall','oppstop','oppfast','oppstraight']:
    for w in ['us','opp','zero','both']:
        vals=[r[key] for r in rows if r['win']==w]
        if vals: print(w,key,round(sum(vals)/len(vals),3),'min',round(min(vals),3),'max',round(max(vals),3))
    print()
print('losses:')
for r in rows:
    if r['win']!='us': print(os.path.basename(r['path']), r)
