#!/usr/bin/env python3
import json, glob, math, sys, statistics
files=glob.glob(sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/1/sim_*.jsonl')
rows=[]
for f in files:
    lines=open(f).read().splitlines(); meta=json.loads(lines[0])
    our=[int(k) for k,v in meta['robots'].items() if 'gpt' in v][0]
    enemy=[int(k) for k,v in meta['robots'].items() if 'gpt' not in v][0]
    last=None; states=[]; winner='?'
    for line in lines[1:]:
        o=json.loads(line)
        if 'winner' in o:
            winner=o['winner']; break
        if 't' not in o: continue
        us={u['i']:u for u in o['u']}
        if our in us and enemy in us:
            a,b=us[our],us[enemy]
            states.append((o['t'],a,b,math.hypot(a['x']-b['x'],a['y']-b['y'])))
    # focus after first 150 ticks as special branches likely active
    late=states[150:]
    if not late: continue
    rows.append((f, winner, states[-1][0], min(s[1]['e'] for s in states), min(s[2]['e'] for s in states),
                 statistics.mean(s[3] for s in late), min(s[3] for s in late),
                 sum(1 for s in late if s[3]<250)/len(late), sum(1 for s in late if s[3]>480)/len(late)))
for r in sorted(rows,key=lambda x:x[2], reverse=True)[:20]:
    print(r)
print('avg late dist', statistics.mean(r[5] for r in rows), 'frac<250', statistics.mean(r[7] for r in rows), 'frac>480', statistics.mean(r[8] for r in rows))
