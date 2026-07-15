import json,glob,math
files=sorted(glob.glob('/logs/rounds/0/sim_*.jsonl'))
def load(fn):
    ticks={}
    for l in open(fn):
        d=json.loads(l)
        if 'u' not in d: continue
        ticks[d['t']]={u['i']:u for u in d['u']}
    return ticks
# realistic: fire only when gun cooled. gunheat after fire = 1+power/5, cools 0.1/tick.
# We aim each tick; fire when heat<=0 and aligned (assume aligned quickly).
def sim(mode, power):
    bs=20-3*power; cool=1+power/5
    hits=0; shots=0
    for fn in files:
        ticks=load(fn); tl=sorted(ticks)
        heat=3.0  # initial
        for t in tl:
            heat=max(0,heat-0.1*(1 if t>tl[0] else 0))
            r=ticks[t]
            if 0 not in r or 1 not in r: continue
            if heat>0: continue
            me=r[0];en=r[1]
            ex,ey=en['x'],en['y'];ev=en['v'];eh=en['bh'];mx,my=me['x'],me['y']
            if mode=='headon': W=1.0
            elif mode=='lead': W=0.0
            elif mode=='hybrid': W=1.0 if abs(ev)<2.0 else 0.0
            lx,ly=ex,ey
            for _ in range(12):
                fd=math.hypot(lx-mx,ly-my);ft=fd/bs
                lx=ex+math.sin(eh)*ev*ft; ly=ey+math.cos(eh)*ev*ft
            px=W*ex+(1-W)*lx; py=W*ey+(1-W)*ly
            fd=math.hypot(px-mx,py-my);ft=fd/bs
            arr=t+int(round(ft))
            cand=[tt for tt in tl if tt>=arr]
            if not cand: continue
            at=cand[0]
            if 1 not in ticks[at]: continue
            aen=ticks[at][1]
            aim=math.atan2(px-mx,py-my)
            bx=mx+math.sin(aim)*bs*(at-t); by=my+math.cos(aim)*bs*(at-t)
            dist=math.hypot(bx-aen['x'],by-aen['y'])
            shots+=1
            if dist<20: hits+=1
            heat=cool
    return hits/shots if shots else 0, shots
for m in ['headon','lead','hybrid']:
    hr,sh=sim(m,3.0)
    print(m, round(hr,3), sh)

print("--- headon hit rate by actual engagement distance ---")
def simdist(power):
    bs=20-3*power; cool=1+power/5
    buckets={}
    for fn in files:
        ticks=load(fn); tl=sorted(ticks)
        heat=3.0
        for t in tl:
            heat=max(0,heat-0.1*(1 if t>tl[0] else 0))
            r=ticks[t]
            if 0 not in r or 1 not in r: continue
            if heat>0: continue
            me=r[0];en=r[1]
            ex,ey=en['x'],en['y'];mx,my=me['x'],me['y']
            d=math.hypot(ex-mx,ey-my)
            fd=d;ft=fd/bs
            arr=t+int(round(ft))
            cand=[tt for tt in tl if tt>=arr]
            if not cand: continue
            at=cand[0]
            if 1 not in ticks[at]: continue
            aen=ticks[at][1]
            aim=math.atan2(ex-mx,ey-my)
            bx=mx+math.sin(aim)*bs*(at-t); by=my+math.cos(aim)*bs*(at-t)
            dist=math.hypot(bx-aen['x'],by-aen['y'])
            b=int(d//100)*100
            buckets.setdefault(b,[0,0])
            buckets[b][1]+=1
            if dist<20: buckets[b][0]+=1
            heat=cool
    for b in sorted(buckets):
        h,s=buckets[b]
        print(f"{b}-{b+100}px: {h/s:.2f} ({s})")
simdist(3.0)
