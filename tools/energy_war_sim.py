#!/usr/bin/env python3
"""Energy-war replay simulator. Replays our firing policy against the enemy's
recorded trajectory in /logs/rounds/N/sim_*.jsonl and reports our final energy,
damage dealt to enemy, and whether we'd kill it. Use to tune W and power tiers.
Usage: python3 energy_war_sim.py /logs/rounds/0"""
import json,glob,math,sys,statistics,os
d=sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/0'
def sim_policy(fn,W,powerfn):
    ls=[json.loads(l) for l in open(fn) if l.strip()]
    hdr=ls[0]['robots']
    ei=[int(k) for k,v in hdr.items() if 'opus' not in v][0]; oi=1-ei
    states={};maxt=0
    for dd in ls:
        t=dd.get('t')
        if 'u' in dd and t is not None:
            cur=states.setdefault(t,{})
            for u in dd['u']: cur[u['i']]=u
            maxt=max(maxt,t)
    seq=[];lastO=None;lastE=None
    for t in range(maxt+1):
        s=states.get(t,{})
        if oi in s:lastO=s[oi]
        if ei in s:lastE=s[ei]
        seq.append((lastO,lastE))
    ourE=100.0;dmg=0.0;gr=0
    for t in range(1,maxt):
        o,e=seq[t]
        if o is None or e is None:continue
        if e.get('s')=='DEAD':break
        if t<gr:continue
        dist=math.hypot(e['x']-o['x'],e['y']-o['y'])
        p=powerfn(dist,ourE,e['e'])
        if p<=0 or ourE<=p+0.5:continue
        bs=20-3*p;fl=dist/bs
        eh=e.get('bh',0);ev=e.get('v',0)
        lx=e['x']+math.sin(eh)*ev*fl;ly=e['y']+math.cos(eh)*ev*fl
        px=W*e['x']+(1-W)*lx;py=W*e['y']+(1-W)*ly
        aim=math.atan2(px-o['x'],py-o['y'])
        bx,by=o['x'],o['y'];bvx=math.sin(aim)*bs;bvy=math.cos(aim)*bs
        hit=False
        for k in range(1,int(dist/bs)+40):
            if t+k>maxt:break
            bx+=bvx;by+=bvy
            _,ee=seq[t+k]
            if ee is None:break
            if math.hypot(bx-ee['x'],by-ee['y'])<18:hit=True;break
            if bx<0 or bx>800 or by<0 or by>600:break
        ourE-=p
        if hit:ourE+=3*p;dmg+=4*p+2*max(p-1,0)
        gr=t+int(1+p/5*10)
    return ourE,dmg
def newp(dist,oe,ee):
    p=3.0 if dist<200 else 2.4 if dist<300 else 1.6 if dist<450 else 1.0
    if oe<ee:p=min(p,1.2)
    return p
fes=[];dmgs=[];k=0;files=sorted(glob.glob(os.path.join(d,'sim_*.jsonl')))
for fn in files:
    fe,dm=sim_policy(fn,0.5,newp);fes.append(fe);dmgs.append(dm)
    if dm>=100:k+=1
print(f'NEW policy over {len(files)} games: ourFinalE avg={statistics.mean(fes):.1f} min={min(fes):.1f} | enemyDmg avg={statistics.mean(dmgs):.1f} | kills={k}/{len(files)}')
