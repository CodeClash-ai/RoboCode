#!/usr/bin/env python3
"""Summarize kcanida__pikachu traces.
Usage: python3 tools/analyze_pikachu.py [/logs/rounds/0]
"""
import glob, json, math, os, statistics, sys

def norm(a):
    while a <= -math.pi: a += 2 * math.pi
    while a > math.pi: a -= 2 * math.pi
    return a

def dist(a,b,c,d): return math.hypot(a-c,b-d)
base = sys.argv[1] if len(sys.argv) > 1 else '/logs/rounds/0'
rows=[]
for f in sorted(glob.glob(base + '/sim_*.jsonl'), key=lambda p: int(os.path.basename(p).split('_')[1].split('.')[0])):
    with open(f, errors='replace') as fh:
        header=json.loads(next(fh)); names=header.get('robots', {})
        mine=[int(i) for i,n in names.items() if 'gpt' in n]
        if not mine: continue
        my=mine[0]; en=[int(i) for i in names if int(i)!=my][0]
        states=[]; last={}; enemy_drops=[]; my_drops=[]
        for line in fh:
            o=json.loads(line); d={u['i']:u for u in o.get('u', [])}
            if my in d and en in d:
                states.append((o['t'], d[my], d[en]))
                for i,u in d.items():
                    if i in last:
                        drop=last[i]-u['e']
                        if .09 < drop <= 3.01:
                            (enemy_drops if i == en else my_drops).append(drop)
                    last[i]=u['e']
        if not states: continue
        mef,enf=states[-1][1],states[-1][2]
        outcome='W' if mef['s']=='ACTIVE' and enf['s']!='ACTIVE' else ('L' if enf['s']=='ACTIVE' and mef['s']!='ACTIVE' else 'D')
        turns=[]; lastbh=None; stops=walls=straight=0; dists=[]
        for t,me,e in states:
            dists.append(dist(me['x'],me['y'],e['x'],e['y']))
            stops += abs(e['v']) < .15
            walls += e['x'] < 70 or e['x'] > 730 or e['y'] < 70 or e['y'] > 530
            if lastbh is not None:
                tr=norm(e['bh']-lastbh); turns.append(tr)
                straight += abs(tr) < .012 and abs(e['v']) > .55
            lastbh=e['bh']
        rows.append(dict(outcome=outcome,t=states[-1][0],me=mef['e'],en=enf['e'],
            stop=stops/len(states),wall=walls/len(states),straight=straight/max(1,len(turns)),
            turn=statistics.mean(abs(x) for x in turns) if turns else 0,
            shots=len(enemy_drops),avgp=statistics.mean(enemy_drops) if enemy_drops else 0,
            myshots=len(my_drops),myavgp=statistics.mean(my_drops) if my_drops else 0,
            avgdist=statistics.mean(dists),mindist=min(dists)))
print('files', len(rows), 'W/L/D', {k:sum(r['outcome']==k for r in rows) for k in 'WLD'})
for oc in 'WLD':
    xs=[r for r in rows if r['outcome']==oc]
    if not xs: continue
    print('\n', oc, len(xs))
    for k in ['t','me','en','stop','wall','straight','turn','shots','avgp','myshots','myavgp','avgdist','mindist']:
        vals=[r[k] for r in xs]
        print(k, round(statistics.mean(vals),3), 'med', round(statistics.median(vals),3))
