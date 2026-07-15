#!/usr/bin/env python3
import json, glob, math, sys

def norm(a):
    while a <= -math.pi: a += 2*math.pi
    while a > math.pi: a -= 2*math.pi
    return a

def dist(a,b,c,d): return math.hypot(a-c,b-d)
def clamp(v,lo,hi): return max(lo,min(hi,v))

def predict(srcx,srcy, ex,ey, heading, vel, turn, bs, typ, vavg=0,tavg=0):
    if typ=='head': return ex,ey
    if typ=='avg':
        vel=clamp(0.45*vel+0.55*vavg,-3.5,3.5); turn=clamp(0.35*turn+0.65*tavg,-.09,.09)
    elif typ=='lin': turn=0
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

files=glob.glob(sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/1/sim_*.jsonl')[:]
errs={k:[] for k in ['head','lin','circ','avg']}
bydist=[]
for f in files:
    states=[]; bullets=[]
    meta=None
    for line in open(f):
        o=json.loads(line)
        if 'robots' in o: meta=o; continue
        if 'u' in o:
            d={u['i']:u for u in o['u']}
            if 0 in d and 1 in d:
                states.append((o['t'], d[0], d[1], o.get('b',[])))
    # map time to enemy pos interpolation nearest
    time_index={t:(me,en) for t,me,en,b in states}
    vavg=tavg=0; lastHead=None
    # detect our bullets newly appearing owner 0; use state at same t. Need absBearing from me to enemy, power b p.
    seen=set()
    for idx,(t,me,en,bslist) in enumerate(states):
        turn=0 if lastHead is None else norm(en['bh']-lastHead)
        vavg=.84*vavg+.16*en['v']; tavg=.84*tavg+.16*turn
        for bi,b in enumerate(bslist):
            if b.get('o')==0 and b.get('s')=='MOVING':
                key=(round(b['x'],1),round(b['y'],1),t,b.get('p'))
                # initial bullet within ~60 of us and not seen; but all bullets persist. new if distance from me roughly speed? 
                if dist(b['x'],b['y'],me['x'],me['y']) < 70:
                    power=b['p']; speed=20-3*power
                    flight=dist(me['x'],me['y'],en['x'],en['y'])/speed
                    target_t=t+round(flight/4)*4 # logs every 4
                    # find nearest future state after bullet arrival
                    fut=min(states[idx:], key=lambda s: abs(s[0]-target_t)) if idx < len(states) else None
                    if fut and abs(fut[0]-target_t)<=8:
                        actual=fut[2]
                        for typ in errs:
                            px,py=predict(me['x'],me['y'],en['x'],en['y'],en['bh'],en['v'],turn,speed,typ,vavg,tavg)
                            errs[typ].append(dist(px,py,actual['x'],actual['y']))
                        bydist.append((dist(me['x'],me['y'],en['x'],en['y']), power))
        lastHead=en['bh']
for k,v in errs.items():
    print(k,len(v),sum(v)/len(v), sorted(v)[len(v)//2])
print('shots approx',len(bydist),'avgdist',sum(d for d,p in bydist)/len(bydist),'avgp',sum(p for d,p in bydist)/len(bydist))
