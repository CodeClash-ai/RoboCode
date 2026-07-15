import json,glob,math
def hitrate(W, power, circ=False, sims=80):
    hits=0;shots=0
    bs=20-3*power
    for fn in sorted(glob.glob('sim_*.jsonl'))[:sims]:
        ls=[json.loads(l) for l in open(fn) if l.strip()]
        hdr=ls[0]['robots']
        ei=[int(k) for k,v in hdr.items() if 'opus' not in v][0]; oi=1-ei
        # build per-tick state arrays
        states={}
        maxt=0
        for d in ls:
            t=d.get('t')
            if 'u' in d and t is not None:
                cur=states.setdefault(t,{})
                for u in d['u']:
                    cur[u['i']]=u
                maxt=max(maxt,t)
        # forward fill
        seq=[]
        lastO=None;lastE=None
        for t in range(maxt+1):
            s=states.get(t,{})
            if oi in s: lastO=s[oi]
            if ei in s: lastE=s[ei]
            seq.append((lastO,lastE))
        # simulate firing every 10 ticks (gun cooldown approx)
        gunready=0
        for t in range(1,maxt):
            o,e=seq[t]
            if o is None or e is None: continue
            if o.get('s')=='DEAD' or e.get('s')=='DEAD': break
            if t<gunready: continue
            # aim
            dist=math.hypot(e['x']-o['x'],e['y']-o['y'])
            flight=dist/bs
            eh=e.get('bh',0);ev=e.get('v',0)
            leadX=e['x']+math.sin(eh)*ev*flight
            leadY=e['y']+math.cos(eh)*ev*flight
            predX=W*e['x']+(1-W)*leadX
            predY=W*e['y']+(1-W)*leadY
            aim=math.atan2(predX-o['x'],predY-o['y'])
            # fire bullet, step forward
            bx,by=o['x'],o['y']
            bvx=math.sin(aim)*bs;bvy=math.cos(aim)*bs
            hit=False
            for k in range(1,int(dist/bs)+40):
                if t+k>maxt: break
                bx+=bvx;by+=bvy
                oe,ee=seq[t+k]
                if ee is None: break
                if math.hypot(bx-ee['x'],by-ee['y'])<18:
                    hit=True;break
                if bx<0 or bx>800 or by<0 or by>600: break
            shots+=1
            if hit:hits+=1
            gunready=t+int(math.ceil(1+power/5*10))  # rough
    return hits/shots if shots else 0, shots

for W in [1.0,0.9,0.7,0.5,0.3,0.0]:
    hr,sh=hitrate(W,3.0)
    print(f"W={W} power3.0 hit={hr:.3f} shots={sh}")

print("--- fine W around 0.5 ---")
for W in [0.4,0.45,0.5,0.55,0.6]:
    hr,sh=hitrate(W,3.0)
    print(f"W={W} hit={hr:.3f}")
print("--- power sweep at W=0.5 ---")
for p in [1.0,1.5,2.0,2.5,3.0]:
    hr,sh=hitrate(0.5,p)
    print(f"power={p} hit={hr:.3f}")
