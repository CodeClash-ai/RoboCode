#!/usr/bin/env python3
"""Quick summary for denssle__megaborsten logs.
Usage: python3 tools/analyze_megaborsten.py /logs/rounds/0
"""
import json, glob, math, os, re, statistics, sys, collections
base = sys.argv[1] if len(sys.argv) > 1 else '/logs/rounds/0'
files = glob.glob(base if '*' in base else os.path.join(base, 'sim_*.jsonl'))
outs=collections.Counter(); lens=[]; dist_win=[]; dist_loss=[]; speeds=[]; turns=[]; near=[]; stop=[]; full=[]; ourdrops=[]; oppdrops=[]; ends=[]
def norm(a): return (a+math.pi)%(2*math.pi)-math.pi
for p in sorted(files):
    with open(p, errors='replace') as fh:
        hdr=json.loads(next(fh)); names=hdr.get('robots',{})
        my=[int(i) for i,n in names.items() if n in ('gpt_5_5','gpt-5-5')]
        op=[int(i) for i,n in names.items() if 'denssle__megaborsten' in n]
        if not my or not op: continue
        my=my[0]; op=op[0]; prev=None; dists=[]; sp=[]; tr=[]; n=s=f=cnt=0; last=None
        for line in fh:
            if not line.startswith('{"t"'): continue
            o=json.loads(line); units={u['i']:u for u in o.get('u',[])}
            if my not in units or op not in units: continue
            me,en=units[my],units[op]
            d=math.hypot(me['x']-en['x'], me['y']-en['y']); dists.append(d); cnt += 1
            sp.append(abs(en['v']))
            if abs(en['v'])<0.15: s+=1
            if abs(en['v'])>7.5: f+=1
            if en['x']<70 or en['x']>hdr.get('w',800)-70 or en['y']<70 or en['y']>hdr.get('h',600)-70: n+=1
            if prev:
                tr.append(abs(norm(en['bh']-prev['eh'])))
                de=prev['ee']-en['e']; dm=prev['me']-me['e']
                if 0.09<=de<=3.01: oppdrops.append(de)
                if 0.09<=dm<=3.01: ourdrops.append(dm)
            prev={'eh':en['bh'],'ee':en['e'],'me':me['e']}; last=(o['t'],me,en,d,p)
        if last:
            t,me,en,d,p=last; lens.append(t); ends.append((me['e'],en['e'],d,t,os.path.basename(p)))
            if me['e']>0 and en['e']<=0: outs['win']+=1; dist_win += dists
            elif en['e']>0 and me['e']<=0: outs['loss']+=1; dist_loss += dists
            elif me['e']>en['e']: outs['active_adv']+=1
            elif en['e']>me['e']: outs['active_behind']+=1
            else: outs['draw']+=1
        speeds += sp; turns += tr
        if cnt: near.append(n/cnt); stop.append(s/cnt); full.append(f/cnt)
print('files',len(files),'outcomes',outs,'avglen',round(statistics.mean(lens),1) if lens else None)
for name,arr in [('win_dist',dist_win),('loss_dist',dist_loss),('opp_speed',speeds),('opp_turn',turns),('near_frac',near),('stop_frac',stop),('full_frac',full),('our_drop',ourdrops),('opp_drop',oppdrops)]:
    if arr:
        arrs=sorted(arr); print(name,'mean',round(statistics.mean(arr),3),'med',round(statistics.median(arr),3),'p10',round(arrs[int(.1*len(arrs))],3),'p90',round(arrs[max(0,int(.9*len(arrs))-1)],3),'n',len(arr))
print('worst end margins:')
for e in sorted(ends, key=lambda x:x[0]-x[1])[:12]: print(e)
# result files compact
for rp in sorted(glob.glob(os.path.join(base,'results_*.txt')))[:3]:
    print(os.path.basename(rp));
    print('\n'.join(l for l in open(rp,errors='replace').read().splitlines() if 'gpt' in l or 'denssle' in l))
