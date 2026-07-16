#!/usr/bin/env python3
import sys,json,glob,math,statistics
path=sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/1'
files=sorted(glob.glob(path+'/sim_*.jsonl'), key=lambda p:int(p.split('_')[-1].split('.')[0]))
rows=[]
for f in files:
    lines=open(f).read().strip().splitlines()
    if not lines: continue
    header=json.loads(lines[0]); names=header['robots']
    myid=oppid=None
    for k,v in names.items():
        if 'gpt' in v: myid=int(k)
        else: oppid=int(k)
    prev=None; myshots=[]; oppdrops=[]; dists=[]; myhits=0; opphits=0; mybs=[]; oppbs=[]; end=None
    low=[]
    winner=''
    for line in lines[1:]:
        o=json.loads(line)
        if 'winner' in o:
            winner=o.get('winner') or ''; break
        us={u['i']:u for u in o.get('u',[])}
        if myid not in us or oppid not in us: continue
        me,op=us[myid],us[oppid]
        d=math.hypot(me['x']-op['x'], me['y']-op['y']); dists.append(d)
        if prev:
            pme,pop=prev
            md=pme['e']-me['e']; od=pop['e']-op['e']
            if 0.09<md<=3.01: myshots.append((o['t'],md,me['e'],op['e'],d))
            if 0.09<od<=3.01: oppdrops.append((o['t'],od,me['e'],op['e'],d))
            if od<-0.01: myhits+=1
            if md<-0.01: opphits+=1
        if me['e']<30: low.append((o['t'],me['e'],op['e'],d))
        prev=(me,op); end=(me,op,o['t'])
    win=('gpt' in winner) or (end and end[0]['e']>0 and end[1]['e']<=0)
    rows.append(dict(f=f.split('/')[-1],win=win,winner=winner,t=end[2] if end else 0,mye=end[0]['e'] if end else 0,oppe=end[1]['e'] if end else 0,avgd=statistics.mean(dists) if dists else 0,mind=min(dists) if dists else 0,myshots=len(myshots),myavg=statistics.mean([x[1] for x in myshots]) if myshots else 0,opp=len(oppdrops),oppavg=statistics.mean([x[1] for x in oppdrops]) if oppdrops else 0,myhits=myhits,opphits=opphits,lowfirst=low[0] if low else None))
for label,rs in [('all',rows),('wins',[r for r in rows if r['win']]),('losses',[r for r in rows if not r['win']])]:
    print(label, len(rs))
    if rs:
        for k in ['t','mye','oppe','avgd','mind','myshots','myavg','opp','oppavg','myhits','opphits']:
            print(k, round(statistics.mean([r[k] for r in rs]),3), end=' ')
        print() 
print('loss details')
for r in [r for r in rows if not r['win']][:50]: print(r)
