import json,glob,math,sys
files=sorted(glob.glob('/logs/rounds/0/sim_*.jsonl'))
if len(sys.argv)>1: files=files[:int(sys.argv[1])]
def load(fn):
    ls=[json.loads(l) for l in open(fn) if l.strip()]
    hdr=ls[0]['robots']; ei=[int(k) for k,v in hdr.items() if 'opus' not in v][0]; oi=1-ei
    frames={}
    for d in ls:
        if 'u' not in d: continue
        um={u['i']:u for u in d['u']}
        if ei in um and oi in um: frames[d.get('t')]=(um[ei],um[oi])
    return frames

def test(mode, power=3.0):
    bs=20-3*power; hits=0; shots=0
    for fn in files:
        fr=load(fn); ts=sorted(fr.keys())
        for idx,t in enumerate(ts):
            e,o=fr[t]
            ex,ey=e['x'],e['y']; ox,oy=o['x'],o['y']
            ev=e.get('v',0); eh=e.get('bh',0)
            # avg turn rate over last N ticks
            dh=0
            n=0; acc=0
            for k in range(1,6):
                if idx-k>=0 and idx-k+1<=idx and ts[idx-k] in fr and ts[idx-k+1] in fr:
                    a=fr[ts[idx-k+1]][0].get('bh',0); b=fr[ts[idx-k]][0].get('bh',0)
                    dd=(a-b+math.pi)%(2*math.pi)-math.pi; acc+=dd; n+=1
            if n: dh=acc/n
            dh=max(-0.15,min(0.15,dh))
            # predict future pos
            if mode=='headon': px,py=ex,ey
            elif mode=='linear':
                lx,ly=ex,ey
                for it in range(15):
                    ft=math.hypot(lx-ox,ly-oy)/bs
                    lx=ex+math.sin(eh)*ev*ft; ly=ey+math.cos(eh)*ev*ft
                px,py=lx,ly
            elif mode=='circ':
                # iterate flight time then step with turn
                ft=0
                for it in range(15):
                    px,py=ex,ey; h=eh
                    steps=int(ft)
                    for s in range(steps):
                        h+=dh; px+=math.sin(h)*ev; py+=math.cos(h)*ev
                    ft=math.hypot(px-ox,py-oy)/bs
            aim=math.atan2(px-ox,py-oy)
            ftr=math.hypot(px-ox,py-oy)/bs
            tgt=t+int(round(ftr))
            if tgt in fr:
                fe=fr[tgt][0]
                bx=ox+math.sin(aim)*bs*ftr; by=oy+math.cos(aim)*bs*ftr
                if math.hypot(bx-fe['x'],by-fe['y'])<18: hits+=1
            shots+=1
    return hits/shots if shots else 0
for m in ['headon','linear','circ']:
    print(m, round(test(m),3))

def bydist(mode='circ',power=3.0):
    bs=20-3*power
    b={}
    for fn in files:
        fr=load(fn); ts=sorted(fr.keys())
        for idx,t in enumerate(ts):
            e,o=fr[t]
            ex,ey=e['x'],e['y']; ox,oy=o['x'],o['y']
            ev=e.get('v',0); eh=e.get('bh',0)
            n=0;acc=0
            for k in range(1,6):
                if idx-k>=0 and ts[idx-k] in fr and ts[idx-k+1] in fr:
                    a=fr[ts[idx-k+1]][0].get('bh',0);bb=fr[ts[idx-k]][0].get('bh',0)
                    dd=(a-bb+math.pi)%(2*math.pi)-math.pi;acc+=dd;n+=1
            dh=acc/n if n else 0; dh=max(-0.15,min(0.15,dh))
            ft=0
            for it in range(15):
                px,py=ex,ey;h=eh
                for s in range(int(ft)):
                    h+=dh;px+=math.sin(h)*ev;py+=math.cos(h)*ev
                ft=math.hypot(px-ox,py-oy)/bs
            aim=math.atan2(px-ox,py-oy);ftr=math.hypot(px-ox,py-oy)/bs
            dist=math.hypot(ex-ox,ey-oy); bk=int(dist//100)*100
            r=b.setdefault(bk,[0,0]);r[1]+=1
            tgt=t+int(round(ftr))
            if tgt in fr:
                fe=fr[tgt][0]
                bx=ox+math.sin(aim)*bs*ftr;byy=oy+math.cos(aim)*bs*ftr
                if math.hypot(bx-fe['x'],byy-fe['y'])<18:r[0]+=1
    for k in sorted(b):
        h,tot=b[k];print('dist %d-%d hitrate=%.2f (n=%d)'%(k,k+100,h/tot if tot else 0,tot))
bydist()
