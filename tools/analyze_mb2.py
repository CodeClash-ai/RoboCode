#!/usr/bin/env python3
import glob,json,math,statistics,sys,os
files=glob.glob(sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/0/sim_*.jsonl')
N=0; wins=loss=draw=0
vals=[]; fireps=[]; dist=[]; wallticks=stops=straight=turns=0; ticks=0; speeds=[]; trates=[]; myhits=[]; enemyhits=[]; lens=[]; endEs=[]
for f in files:
    lines=open(f).read().splitlines();
    if not lines: continue
    meta=json.loads(lines[0]); robots=meta['robots']; ids={v:k for k,v in robots.items()}
    eid=ids.get('gjgomez__mb2'); mid=ids.get('gpt_5_5') or ids.get('gpt-5-5')
    if eid is None or mid is None: continue
    prev=None; laste=None; lastm=None; lastbh=None; lastpos=None
    end=None
    for line in lines[1:]:
        d=json.loads(line)
        if 'u' not in d: continue
        end=d; ru={str(u['i']):u for u in d['u']}
        if eid not in ru or mid not in ru: continue
        e=ru[eid]; m=ru[mid]
        ticks+=1
        x,y=e['x'],e['y']; w,h=meta['w'],meta['h']
        if min(x,w-x,y,h-y)<40: wallticks+=1
        if abs(e['v'])<.15: stops+=1
        speeds.append(abs(e['v']))
        if lastbh is not None:
            tr=((e['bh']-lastbh+math.pi)%(2*math.pi)-math.pi); trates.append(abs(tr))
            if abs(e['v'])>.5 and abs(tr)<.012: straight+=1
            if abs(tr)>.035: turns+=1
        lastbh=e['bh']
        if laste is not None:
            de=laste-e['e']
            if .09<=de<=3.01: fireps.append(de)
        laste=e['e']; lastm=m['e']
        dx=e['x']-m['x']; dy=e['y']-m['y']; dist.append(math.hypot(dx,dy))
    if end:
        ru={str(u['i']):u for u in end['u']};
        if eid in ru and mid in ru:
            ee=ru[eid]['e']; me=ru[mid]['e']; endEs.append((me,ee));
            if me>0 and ee<=0: wins+=1
            elif ee>0 and me<=0: loss+=1
            else: draw+=1
        lens.append(end.get('t',0))

def avg(a): return statistics.mean(a) if a else 0
print('files',len(files),'outcomes',wins,loss,draw,'avg len',avg(lens))
print('enemy speed avg/max stop wall straight turn',avg(speeds),max(speeds or [0]),stops/max(1,ticks),wallticks/max(1,ticks),straight/max(1,len(trates)),turns/max(1,len(trates)))
print('turn avg',avg(trates),'fire count/game',len(fireps)/max(1,len(files)),'power avg med counts',avg(fireps),statistics.median(fireps) if fireps else 0, {round(p,1):sum(1 for x in fireps if abs(x-p)<.05) for p in [0.1,0.5,1,1.5,2,2.5,3]})
print('dist avg med min max',avg(dist),statistics.median(dist),min(dist),max(dist))
print('end energy my/enemy avg',avg([x for x,y in endEs]),avg([y for x,y in endEs]))
