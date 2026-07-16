#!/usr/bin/env python3
import sys, glob, json, math, os, statistics
paths=[]
for a in sys.argv[1:] or ['/logs/rounds/0']:
    if os.path.isdir(a): paths += glob.glob(os.path.join(a,'sim_*.jsonl'))
    else: paths += glob.glob(a)
paths=sorted(paths, key=lambda p:int(os.path.basename(p).split('_')[1].split('.')[0]) if '_' in os.path.basename(p) else 0)
our='gpt'; opp='pez__wallspoethaiku'
outs={'win':0,'loss':0,'draw':0}
rows=[]
def nearwall(x,y,w,h,m=40): return x<m or y<m or x>w-m or y>h-m
for p in paths:
    with open(p) as f:
        meta=json.loads(next(f)); w=meta['w']; h=meta['h']; names=meta['robots']
        oid=[int(k) for k,v in names.items() if opp in v][0]; mid=[int(k) for k,v in names.items() if 'gpt' in v][0]
        prev={}; edrops=[]; odrops=[]; dists=[]; owalls=0; ostop=0; ofast=0; oturns=[]; ospeeds=[]; ticks=0; close=0; myE=[]; opE=[]; last_heading=None; endst={}
        for line in f:
            rec=json.loads(line); units={u['i']:u for u in rec.get('u',[])}
            if oid in units and mid in units:
                o=units[oid]; m=units[mid]
                dx=o['x']-m['x']; dy=o['y']-m['y']; d=math.hypot(dx,dy); dists.append(d); ticks+=1
                if d<250: close+=1
                if nearwall(o['x'],o['y'],w,h): owalls+=1
                if abs(o['v'])<.2: ostop+=1
                if abs(o['v'])>7.5: ofast+=1
                ospeeds.append(abs(o['v']))
                if last_heading is not None:
                    dh=(o['bh']-last_heading+math.pi)%(2*math.pi)-math.pi; oturns.append(abs(dh))
                last_heading=o['bh']; myE.append(m['e']); opE.append(o['e'])
                for id,u in [(oid,o),(mid,m)]:
                    if id in prev:
                        de=prev[id]-u['e']
                        if .09<de<=3.01: (odrops if id==oid else edrops).append(de)
                    prev[id]=u['e']
            for u in rec.get('u',[]): endst[u['i']]=u['s']
    ms=endst.get(mid,'?'); opp_status=endst.get(oid,'?')
    if ms=='WINNER' or (ms=='ACTIVE' and opp_status!='ACTIVE'): out='win'
    elif opp_status=='WINNER' or (opp_status=='ACTIVE' and ms!='ACTIVE'): out='loss'
    else:
        # compare final energy if both disabled/dead? no
        out='draw'
    outs[out]+=1
    rows.append(dict(path=p,out=out,t=ticks,avgd=statistics.mean(dists) if dists else 0,mind=min(dists) if dists else 0, close=close/max(1,ticks),owall=owalls/max(1,ticks),ostop=ostop/max(1,ticks),ofast=ofast/max(1,ticks),ospeed=statistics.mean(ospeeds) if ospeeds else 0,oturn=statistics.mean(oturns) if oturns else 0,oshots=len(odrops),opavg=statistics.mean(odrops) if odrops else 0,myshots=len(edrops),mypavg=statistics.mean(edrops) if edrops else 0,myend=myE[-1] if myE else 0,opend=opE[-1] if opE else 0))
print('n',len(rows),outs)
for key in ['t','avgd','mind','close','owall','ostop','ofast','ospeed','oturn','oshots','opavg','myshots','mypavg','myend','opend']:
    for out in ['win','loss','draw']:
        vals=[r[key] for r in rows if r['out']==out]
        if vals: print(key,out,round(statistics.mean(vals),3), 'med', round(statistics.median(vals),3), 'n',len(vals))
print('worst losses')
for r in sorted([r for r in rows if r['out']=='loss'], key=lambda x:x['myend'])[:20]: print(os.path.basename(r['path']), r)
