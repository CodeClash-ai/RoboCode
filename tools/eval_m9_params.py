#!/usr/bin/env python3
import json,glob,math,sys,os,statistics

def norm(a): return (a+math.pi)%(2*math.pi)-math.pi
def dist(a,b,c,d): return math.hypot(a-c,b-d)
def clamp(v,lo,hi): return max(lo,min(hi,v))
def predict(srcx,srcy, ex,ey, heading, vel, turn, bs, cv, ev, cap, ct=0, et=0, tcap=0):
    vel=clamp(cv*vel+ev, -cap, cap); turn=clamp(ct*turn+et, -tcap, tcap) if tcap else 0
    px,py,h=ex,ey,heading; t=0
    while True:
        t+=1
        if t*bs >= dist(srcx,srcy,px,py) or t>=85: break
        if abs(turn)>0.0005: h+=turn
        px += math.sin(h)*vel; py += math.cos(h)*vel
        if not (18<px<782 and 18<py<582):
            px=clamp(px,18,782); py=clamp(py,18,582); break
    return px,py
shots=[]
for f in glob.glob('/logs/rounds/0/sim_*.jsonl')[:40]:
    fh=open(f); header=json.loads(next(fh)); names=header['robots']; my=[int(i) for i,n in names.items() if n in ('gpt_5_5','gpt-5-5')][0]; enid=[int(i) for i in names if int(i)!=my][0]
    states=[]
    for line in fh:
        if not line.strip(): continue
        o=json.loads(line)
        if 'u' in o:
            d={u['i']:u for u in o['u']}
            if my in d and enid in d: states.append((o['t'],d[my],d[enid],o.get('b',[])))
    vavg=tavg=0; lh=None
    for idx,(t,me,en,bslist) in enumerate(states):
        turn=0 if lh is None else norm(en['bh']-lh)
        vavg=.84*vavg+.16*en['v']; tavg=.84*tavg+.16*turn
        for b in bslist:
            if b.get('o')==my and b.get('s')=='MOVING' and dist(b['x'],b['y'],me['x'],me['y'])<70:
                p=b['p']; sp=20-3*p; flight=dist(me['x'],me['y'],en['x'],en['y'])/sp; target=t+round(flight/4)*4
                fut=min(states[idx:], key=lambda s: abs(s[0]-target))
                if abs(fut[0]-target)<=8: shots.append((me,en,turn,vavg,tavg,sp,fut[2],p))
        lh=en['bh']
shots=shots[::5]
print('shots',len(shots))
best=[]
for cv in [0,0.15,0.25,0.35,0.45,0.55,0.7,0.85,1.0]:
 for evc in [0,0.15,0.25,0.35,0.45,0.55,0.7,0.85,1.0]:
  for cap in [1.0,1.5,2.0,2.2,2.6,3.0,3.5,4.5,8.0]:
   errs=[]
   for me,en,turn,vavg,tavg,sp,act,p in shots:
    px,py=predict(me['x'],me['y'],en['x'],en['y'],en['bh'],en['v'],turn,sp,cv,evc*vavg,cap)
    errs.append(dist(px,py,act['x'],act['y']))
   best.append((sum(errs)/len(errs), statistics.median(errs), cv,evc,cap))
for row in sorted(best)[:20]: print(row)
