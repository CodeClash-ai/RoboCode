import json,glob,math,statistics,collections,sys,os
files=sorted(glob.glob(sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/0/sim_*.jsonl'), key=lambda p:int(os.path.basename(p).split('_')[-1].split('.')[0]))
allstats=[]; losses=[]
for f in files:
    meta=None; prev=None; stats=collections.Counter(); vals=collections.defaultdict(list); drops=[]; ourshots=[]; oppshots=[]
    last_e=[None,None]; last_status=['','']; winner=None; end=None
    for line in open(f):
        if not line.strip(): continue
        d=json.loads(line)
        if 'robots' in d: meta=d; continue
        if 't' not in d: continue
        t=d['t']; us=opp=None
        for u in d['u']:
            if u['i']==0: opp=u
            elif u['i']==1: us=u
        if not us or not opp: continue
        if opp['s']=='ACTIVE' and us['s']=='ACTIVE':
            dx=opp['x']-us['x']; dy=opp['y']-us['y']; dist=math.hypot(dx,dy)
            vals['dist'].append(dist); vals['ov'].append(abs(opp['v'])); vals['uv'].append(abs(us['v']))
            if abs(opp['v'])<.15: stats['opp_stop']+=1
            if min(opp['x'],800-opp['x'],opp['y'],600-opp['y'])<70: stats['opp_wall']+=1
            if len(vals['opp_h'])>0:
                tr=abs((opp['bh']-vals['opp_h'][-1]+math.pi)%(2*math.pi)-math.pi)
                vals['turn'].append(tr)
                if tr<.012 and abs(opp['v'])>.5: stats['opp_straight']+=1
            vals['opp_h'].append(opp['bh'])
            # radial toward us: opp vel projected on bearing from us to opp (positive away?) enemyRadial v*cos(enemyHeading-absBearing)
            absb=math.atan2(dx,dy)
            rv=opp['v']*math.cos(opp['bh']-absb)
            vals['radial'].append(rv)
        for b in d.get('b',[]):
            # Count only newly-fired bullets near their owner.  The trace lists each
            # moving bullet every tick, so counting all MOVING entries inflates shots.
            if b.get('s') == 'MOVING':
                if b['o'] == 0 and math.hypot(b['x'] - opp['x'], b['y'] - opp['y']) < 70:
                    oppshots.append((t, b['p']))
                elif b['o'] == 1 and math.hypot(b['x'] - us['x'], b['y'] - us['y']) < 70:
                    ourshots.append((t, b['p']))
        if last_e[0] is not None:
            for i,u in [(0,opp),(1,us)]:
                de=last_e[i]-u['e']
                if de>0.09 and de<=3.1:
                    # drop may bullet/hit/wall; collect
                    if i==0: drops.append((t,de))
        last_e=[opp['e'],us['e']]; last_status=[opp['s'],us['s']]; end=(opp,us,t)
    if end:
        opp,us,t=end
        if us['e']>opp['e'] and us['s']!='DEAD': winner='us'
        elif opp['e']>us['e'] and opp['s']!='DEAD': winner='opp'
        elif us['s']=='DEAD' and opp['s']!='DEAD': winner='opp'
        elif opp['s']=='DEAD' and us['s']!='DEAD': winner='us'
        else: winner='draw'
    n=len(vals['dist']) or 1
    rec=dict(file=os.path.basename(f), winner=winner, end_t=end[2] if end else None, us_e=end[1]['e'] if end else 0, opp_e=end[0]['e'] if end else 0,
             avg_dist=statistics.mean(vals['dist']) if vals['dist'] else 0, min_dist=min(vals['dist']) if vals['dist'] else 0,
             opp_speed=statistics.mean(vals['ov']) if vals['ov'] else 0,
             opp_stop=stats['opp_stop']/n, opp_wall=stats['opp_wall']/n, opp_straight=stats['opp_straight']/n,
             turn=statistics.mean(vals['turn']) if vals['turn'] else 0, medturn=statistics.median(vals['turn']) if vals['turn'] else 0,
             radial=statistics.mean(vals['radial']) if vals['radial'] else 0,
             oppshots=len(oppshots), oppp=statistics.mean([p for _,p in oppshots]) if oppshots else 0,
             ourshots=len(ourshots), ourp=statistics.mean([p for _,p in ourshots]) if ourshots else 0)
    allstats.append(rec)
    if winner!='us': losses.append(rec)
print('games',len(allstats),collections.Counter(r['winner'] for r in allstats))
for k in ['end_t','us_e','opp_e','avg_dist','min_dist','opp_speed','opp_stop','opp_wall','opp_straight','turn','medturn','radial','oppshots','oppp','ourshots','ourp']:
    xs=[r[k] for r in allstats]
    print(k, 'mean', round(statistics.mean(xs),3),'med',round(statistics.median(xs),3),'min',round(min(xs),3),'max',round(max(xs),3))
print('losses/draws')
for r in sorted(losses,key=lambda x:x['file'])[:50]: print(r)
print('worst low us energy wins')
for r in sorted([r for r in allstats if r['winner']=='us'], key=lambda x:x['us_e'])[:10]: print(r)
