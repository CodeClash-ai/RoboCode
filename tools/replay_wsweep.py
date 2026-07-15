import json,glob,math

def load(fn):
    frames={}
    meta=None
    for l in open(fn):
        d=json.loads(l)
        if 'u' not in d: continue
        t=d['t']
        pos={}
        for u in d['u']:
            pos[u['i']]=u
        frames[t]=pos
    return frames

def sim_hit(fn, W, power=3.0):
    frames=load(fn)
    ts=sorted(frames.keys())
    bs=20-3*power
    shots=0; hits=0
    prevh=None
    for i,t in enumerate(ts):
        f=frames[t]
        if 0 not in f or 1 not in f: continue
        me=f[0]; en=f[1]
        # only "fire" when gun would be cool: simulate firing every ~ (gunheat) ticks
        # simplest: fire every tick we have data, measure fraction that would hit
        ex,ey=en['x'],en['y']; eh=en['rh']; ev=en['v']
        mx,my=me['x'],me['y']
        # predict
        lx,ly=ex,ey
        for it in range(12):
            fd=math.hypot(lx-mx,ly-my); ft=fd/bs
            lx=ex+math.sin(eh)*ev*ft
            ly=ey+math.cos(eh)*ev*ft
        px=W*ex+(1-W)*lx; py=W*ey+(1-W)*ly
        aim=math.atan2(px-mx,py-my)
        fd=math.hypot(ex-mx,ey-my); ft=int(round(fd/bs))
        # where is enemy after ft ticks (actual)
        tt=t+ft
        if tt not in frames or 1 not in frames[tt]: continue
        real=frames[tt][1]
        # bullet travels from (mx,my) along aim; check if within robot (~18px) of real pos at arrival
        bx=mx+math.sin(aim)*bs*ft
        by=my+math.cos(aim)*bs*ft
        shots+=1
        if math.hypot(bx-real['x'],by-real['y'])<20:
            hits+=1
    return shots,hits

import sys
files=sorted(glob.glob('/logs/rounds/0/sim_*.jsonl'))[:80]
for W in [0.0,0.25,0.5,0.75,1.0]:
    S=0;H=0
    for fn in files:
        s,h=sim_hit(fn,W); S+=s; H+=h
    print(f'W={W} hit={H/S*100:.1f}% ({H}/{S})')
