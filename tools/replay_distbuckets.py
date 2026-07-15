import json,glob,math
def load(fn):
    frames={}
    for l in open(fn):
        d=json.loads(l)
        if 'u' not in d: continue
        frames[d['t']]={u['i']:u for u in d['u']}
    return frames
files=sorted(glob.glob('/logs/rounds/0/sim_*.jsonl'))[:80]
buckets={}
for fn in files:
    frames=load(fn); ts=sorted(frames)
    for t in ts:
        f=frames[t]
        if 0 not in f or 1 not in f: continue
        me,en=f[0],f[1]
        mx,my=me['x'],me['y']; ex,ey=en['x'],en['y']
        dist=math.hypot(ex-mx,ey-my)
        power=3.0; bs=20-3*power
        aim=math.atan2(ex-mx,ey-my)  # head-on W=1.0
        ft=int(round(dist/bs))
        tt=t+ft
        if tt not in frames or 1 not in frames[tt]: continue
        real=frames[tt][1]
        bx=mx+math.sin(aim)*bs*ft; by=my+math.cos(aim)*bs*ft
        hit=math.hypot(bx-real['x'],by-real['y'])<20
        b=int(dist//100)*100
        buckets.setdefault(b,[0,0])
        buckets[b][0]+=1
        if hit: buckets[b][1]+=1
for b in sorted(buckets):
    s,h=buckets[b]
    print(f'{b}-{b+100}px: {h/s*100:.0f}% ({h}/{s})')
