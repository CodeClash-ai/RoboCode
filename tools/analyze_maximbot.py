#!/usr/bin/env python3
"""Quick trace summary for mgalushka__maximbot matchups.
Usage: python3 tools/analyze_maximbot.py /logs/rounds/0
"""
import glob, json, math, statistics, sys, collections
root = sys.argv[1] if len(sys.argv) > 1 else '/logs/rounds/0'
files = sorted(glob.glob(root + '/sim_*.jsonl'), key=lambda p: int(p.rsplit('_',1)[1].split('.')[0]))
rows=[]
for f in files:
    with open(f) as fh:
        header=json.loads(next(fh))
        names=header.get('robots',{})
        my=[int(i) for i,n in names.items() if n in ('gpt_5_5','gpt-5-5')]
        if not my: continue
        mid=my[0]; eid=[int(i) for i in names if int(i)!=mid][0]
        lastE={}; lastHead=None
        ticks=stops=walls=straight=0; speeds=[]; turns=[]; dists=[]; drops=[]; mydrops=[]; mind=999; end=(0,0)
        for line in fh:
            if not line.strip(): continue
            o=json.loads(line)
            if 'u' not in o: continue
            d={u['i']:u for u in o['u']}
            if mid not in d or eid not in d: continue
            me,en=d[mid],d[eid]; end=(me['e'],en['e']); ticks+=1
            dist=math.hypot(en['x']-me['x'], en['y']-me['y']); dists.append(dist); mind=min(mind,dist)
            speeds.append(abs(en['v']))
            if abs(en['v'])<.1: stops+=1
            if en['x']<70 or en['x']>730 or en['y']<70 or en['y']>530: walls+=1
            if lastHead is not None:
                tr=abs((en['bh']-lastHead+math.pi)%(2*math.pi)-math.pi); turns.append(tr)
                if abs(en['v'])>.5 and tr<.012: straight+=1
            lastHead=en['bh']
            if eid in lastE:
                drop=lastE[eid]-en['e']
                if .09<=drop<=3.01: drops.append(drop)
            if mid in lastE:
                drop=lastE[mid]-me['e']
                if .09<=drop<=3.01: mydrops.append(drop)
            lastE[eid]=en['e']; lastE[mid]=me['e']
        winner='us' if end[0]>0 and end[1]<=0 else ('opp' if end[1]>0 and end[0]<=0 else 'draw')
        rows.append(dict(file=f,winner=winner,ticks=ticks,our=end[0],opp=end[1],avgdist=statistics.mean(dists),mind=mind,
            speed=statistics.mean(speeds),turn=statistics.mean(turns) if turns else 0,stop=stops/max(1,ticks),wall=walls/max(1,ticks),
            straight=straight/max(1,ticks),shots=len(drops),pavg=statistics.mean(drops) if drops else 0, ourhits=len(mydrops), ourpavg=statistics.mean(mydrops) if mydrops else 0))
print('files',len(rows),'winners',collections.Counter(r['winner'] for r in rows))
for k in ['ticks','our','opp','avgdist','mind','speed','turn','stop','wall','straight','shots','pavg','ourhits','ourpavg']:
    vals=[r[k] for r in rows]
    print(k, 'mean', round(statistics.mean(vals),3), 'min', round(min(vals),3), 'max', round(max(vals),3))
print('non-wins:')
for r in rows:
    if r['winner']!='us': print(r)
