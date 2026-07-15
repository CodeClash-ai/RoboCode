import json,glob,math,statistics,sys
files=sorted(glob.glob('/logs/rounds/0/sim_*.jsonl'))
if len(sys.argv)>1: files=files[:int(sys.argv[1])]

def load(fn):
    ls=[json.loads(l) for l in open(fn) if l.strip()]
    hdr=ls[0]['robots']; ei=[int(k) for k,v in hdr.items() if 'opus' not in v][0]; oi=1-ei
    frames={}
    for d in ls:
        if 'u' not in d: continue
        t=d.get('t')
        um={u['i']:u for u in d['u']}
        if ei in um and oi in um:
            frames[t]=(um[ei],um[oi])
    return frames

def hitrate(W, power, circ=False):
    hits=0; shots=0
    bs=20-3*power
    for fn in files:
        fr=load(fn)
        ts=sorted(fr.keys())
        gunheat=0
        for idx,t in enumerate(ts):
            if gunheat>0: gunheat-=1; continue
            e,o=fr[t]
            ex,ey=e['x'],e['y']; ox,oy=o['x'],o['y']
            ev=e.get('v',0); eh=e.get('bh',0)
            # heading delta estimate
            dh=0
            if idx>=1 and ts[idx-1] in fr:
                pe=fr[ts[idx-1]][0]
                dh=eh-pe.get('bh',eh)
                dh=(dh+math.pi)%(2*math.pi)-math.pi
                dh=max(-0.2,min(0.2,dh))
            # predict
            lx,ly=ex,ey
            ch=eh
            for it in range(20):
                fd=math.hypot(lx-ox,ly-oy); ft=fd/bs
                if circ:
                    # step position with turning
                    lx=ex; ly=ey; h=eh
                    steps=int(ft)+1
                    for s in range(steps):
                        h+=dh
                        lx+=math.sin(h)*ev
                        ly+=math.cos(h)*ev
                    break
                else:
                    lx=ex+math.sin(eh)*ev*ft
                    ly=ey+math.cos(eh)*ev*ft
            predx=W*ex+(1-W)*lx; predy=W*ey+(1-W)*ly
            aim=math.atan2(predx-ox,predy-oy)
            ft_real=math.hypot(predx-ox,predy-oy)/bs
            # check actual future pos
            tgt=t+int(round(ft_real))
            if tgt in fr:
                fe=fr[tgt][0]
                # bullet travel pos
                bx=ox+math.sin(aim)*bs*ft_real
                by=oy+math.cos(aim)*bs*ft_real
                if math.hypot(bx-fe['x'],by-fe['y'])<18: hits+=1
            shots+=1
            gunheat=int(1+power/5)*1  # rough
    return hits/shots if shots else 0

for W in [0.0,0.25,0.5,0.75,1.0]:
    print('W=%.2f linear hit=%.3f'%(W,hitrate(W,3.0)))
print('circular hit=%.3f'%(hitrate(0.0,3.0,circ=True)))
