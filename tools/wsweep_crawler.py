import json,glob,math
files=sorted(glob.glob('/logs/rounds/0/sim_*.jsonl'))

def load(fn):
    ls=[json.loads(l) for l in open(fn) if l.strip()]
    hdr=ls[0]['robots']
    ei=[int(k) for k,v in hdr.items() if 'opus' not in v][0]; oi=1-ei
    ticks=[]
    for d in ls:
        if 'u' not in d: continue
        us={u['i']:u for u in d['u']}
        if ei not in us or oi not in us: continue
        ticks.append((us[oi],us[ei]))  # (me, enemy)
    return ticks

def enemy_turn(ticks):
    # estimate per-tick turn rate EMA at each index
    rates=[0.0]*len(ticks)
    prev=None
    ema=0.0
    for i,(me,en) in enumerate(ticks):
        bh=en.get('bh',0)
        if prev is not None:
            dh=bh-prev
            while dh>math.pi: dh-=2*math.pi
            while dh<-math.pi: dh+=2*math.pi
            ema=0.6*ema+0.4*dh
        rates[i]=ema
        prev=bh
    return rates

def sweep(W, power, slice_files, circular=False):
    hits=0; fires=0
    for fn in slice_files:
        ticks=load(fn)
        rates=enemy_turn(ticks)
        bs=20-3*power
        n=len(ticks)
        for i in range(0,n-1,2):
            me,en=ticks[i]
            ex,ey,ev=en['x'],en['y'],en.get('v',0)
            ebh=en.get('bh',0)
            tr=rates[i] if circular else 0.0
            # iterate predicted position
            px,py=ex,ey
            for it in range(14):
                dist=math.hypot(px-me['x'],py-me['y'])
                t=dist/bs
                # head-on pos = current; lead pos = stepped
                lx,ly=ex,ey; h=ebh
                for s in range(int(t)):
                    h+=tr
                    lx+=ev*math.sin(h)
                    ly+=ev*math.cos(h)
                px=W*ex+(1-W)*lx
                py=W*ey+(1-W)*ly
            # aim angle
            aim=math.atan2(px-me['x'],py-me['y'])
            dist=math.hypot(px-me['x'],py-me['y'])
            t=int(dist/bs)
            if i+t>=n: continue
            # bullet travels straight along aim
            bx,by=me['x'],me['y']
            fires+=1
            fen=ticks[i+t][1]
            tx,ty=fen['x'],fen['y']
            # bullet final pos
            bdist=bs*t
            bfx=me['x']+bdist*math.sin(aim)
            bfy=me['y']+bdist*math.cos(aim)
            if math.hypot(bfx-tx,bfy-ty)<18: hits+=1
    return hits/fires if fires else 0

sl=files[:80]
for W in [0.0,0.25,0.5,0.75,0.9,1.0]:
    print('W=%.2f (linear/headon)'%W, round(sweep(W,2.0,sl),3))
print('CIRC W=0.0', round(sweep(0.0,2.0,sl,circular=True),3))
print('CIRC W=0.25', round(sweep(0.25,2.0,sl,circular=True),3))
