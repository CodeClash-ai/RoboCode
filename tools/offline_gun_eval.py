#!/usr/bin/env python3
"""Rough offline replay of gun prediction errors from sim_*.jsonl traces.

Usage:
    python3 tools/offline_gun_eval.py '/logs/rounds/1/sim_*.jsonl'

It detects our robot by name (gpt_5_5/gpt-5-5) instead of assuming id 0;
older versions of this helper got misleading results when the harness swapped ids.
"""
import json, glob, math, sys

def norm(a):
    while a <= -math.pi: a += 2*math.pi
    while a > math.pi: a -= 2*math.pi
    return a

def dist(a,b,c,d): return math.hypot(a-c,b-d)
def clamp(v,lo,hi): return max(lo,min(hi,v))

def predict(srcx,srcy, ex,ey, heading, vel, turn, bs, typ, vavg=0,tavg=0, wall=False):
    if typ == 'head': return ex,ey
    if typ == 'avg':
        if wall:
            vel=clamp(0.25*vel+0.35*vavg,-2.2,2.2); turn=0
        else:
            vel=clamp(0.45*vel+0.55*vavg,-3.5,3.5); turn=clamp(0.35*turn+0.65*tavg,-.09,.09)
    elif typ == 'lin':
        turn=0
    px,py,h=ex,ey,heading
    t=0
    while True:
        t+=1
        if t*bs >= dist(srcx,srcy,px,py) or t>=85: break
        if abs(turn)>.0005: h += turn
        px += math.sin(h)*vel; py += math.cos(h)*vel
        if not (18<px<782 and 18<py<582):
            px=clamp(px,18,782); py=clamp(py,18,582); break
    return px,py

pattern = sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/1/sim_*.jsonl'
errs={k:[] for k in ['head','lin','circ','avg','wallavg']}
shots=[]
for f in glob.glob(pattern):
    with open(f, errors='replace') as fh:
        header=json.loads(next(fh))
        names=header.get('robots', {})
        my_ids=[int(i) for i,n in names.items() if n in ('gpt_5_5','gpt-5-5')]
        if not my_ids or len(names) < 2:
            continue
        my_id=my_ids[0]
        enemy_ids=[int(i) for i in names if int(i) != my_id]
        if not enemy_ids:
            continue
        enemy_id=enemy_ids[0]
        states=[]
        for line in fh:
            o=json.loads(line)
            if 'u' in o:
                d={u['i']:u for u in o['u']}
                if my_id in d and enemy_id in d:
                    states.append((o['t'], d[my_id], d[enemy_id], o.get('b',[])))
    vavg=tavg=0; lastHead=None
    for idx,(t,me,en,bslist) in enumerate(states):
        turn=0 if lastHead is None else norm(en['bh']-lastHead)
        vavg=.84*vavg+.16*en['v']; tavg=.84*tavg+.16*turn
        wall = en['x'] < 70 or en['x'] > header.get('w',800)-70 or en['y'] < 70 or en['y'] > header.get('h',600)-70
        for b in bslist:
            if b.get('o')==my_id and b.get('s')=='MOVING' and dist(b['x'],b['y'],me['x'],me['y']) < 70:
                power=b['p']; speed=20-3*power
                flight=dist(me['x'],me['y'],en['x'],en['y'])/speed
                target_t=t+round(flight/4)*4 # traces are often sampled every 4 ticks
                fut=min(states[idx:], key=lambda s: abs(s[0]-target_t)) if idx < len(states) else None
                if fut and abs(fut[0]-target_t)<=8:
                    actual=fut[2]
                    for typ in ['head','lin','circ','avg']:
                        px,py=predict(me['x'],me['y'],en['x'],en['y'],en['bh'],en['v'],turn,speed,typ,vavg,tavg)
                        errs[typ].append(dist(px,py,actual['x'],actual['y']))
                    px,py=predict(me['x'],me['y'],en['x'],en['y'],en['bh'],en['v'],turn,speed,'avg',vavg,tavg,wall=True)
                    errs['wallavg'].append(dist(px,py,actual['x'],actual['y']))
                    shots.append((dist(me['x'],me['y'],en['x'],en['y']), power, wall))
        lastHead=en['bh']

for k,v in errs.items():
    if v:
        print(k, len(v), sum(v)/len(v), sorted(v)[len(v)//2])
if shots:
    print('shots approx',len(shots),'avgdist',sum(d for d,p,w in shots)/len(shots),'avgp',sum(p for d,p,w in shots)/len(shots),'wallfrac',sum(1 for d,p,w in shots if w)/len(shots))
