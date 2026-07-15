#!/usr/bin/env python3
import glob,json,math,statistics,sys
pat=sys.argv[1] if len(sys.argv)>1 else '/logs/rounds/1/sim_*.jsonl'
shots=[]
for f in glob.glob(pat):
    objs=[json.loads(l) for l in open(f)]
    robots=objs[0]['robots']; me=int([k for k,v in robots.items() if 'gpt' in v][0]); opp=1-me if set(map(int,robots))=={0,1} else int([k for k in robots if int(k)!=me][0])
    frames=[]; prevMeE=None
    for fr in objs[1:]:
        if 'u' not in fr: continue
        units={u['i']:u for u in fr['u']}
        if me in units and opp in units:
            m=units[me]; e=units[opp]
            if prevMeE is not None:
                drop=prevMeE-m['e']
                if 0.1<=drop<=3.01:
                    shots.append((len(frames),drop,m,e,frames.copy()))
            prevMeE=m['e']
            frames.append((fr.get('t'),m,e))
# build direct access by global shot includes copied frames only bad; instead easier second pass produce list with future frames
shots=[]
for f in glob.glob(pat):
    objs=[json.loads(l) for l in open(f)]
    robots=objs[0]['robots']; me=int([k for k,v in robots.items() if 'gpt' in v][0]); opp=int([k for k in robots if int(k)!=me][0])
    arr=[]; prevMeE=None
    for fr in objs[1:]:
        if 'u' not in fr: continue
        units={u['i']:u for u in fr['u']}
        if me in units and opp in units:
            arr.append((fr['t'],units[me],units[opp]))
    for idx,(t,m,e) in enumerate(arr):
        if idx==0: continue
        prev=arr[idx-1][1]['e']; drop=prev-m['e']
        if 0.1<=drop<=3.01:
            shots.append((arr,idx,drop))
print('shots',len(shots))
W,H=800,600
def clamp(x,lo,hi): return min(max(x,lo),hi)
def err_for(kind,param=0,power_mode='actual'):
    errs=[]
    for arr,idx,p in shots:
        t,m,e=arr[idx]
        power=p if power_mode=='actual' else float(power_mode)
        bs=20-3*power
        dist=math.hypot(m['x']-e['x'],m['y']-e['y'])
        travel=max(1,int(round(dist/bs)))
        j=min(len(arr)-1, idx+travel)
        _,_,ef=arr[j]
        if kind=='head': px,py=e['x'],e['y']
        elif kind=='lin':
            px=e['x']+math.sin(e['bh'])*e['v']*travel*param; py=e['y']+math.cos(e['bh'])*e['v']*travel*param
        elif kind=='avgv':
            # avg previous 10 velocity
            vs=[arr[k][2]['v'] for k in range(max(0,idx-10),idx+1)]
            v=sum(vs)/len(vs)
            px=e['x']+math.sin(e['bh'])*v*travel*param; py=e['y']+math.cos(e['bh'])*v*travel*param
        elif kind=='mom':
            # use last nonzero dir * min(abs avg, param)
            vs=[arr[k][2]['v'] for k in range(max(0,idx-20),idx+1)]
            v=sum(vs)/len(vs)
            px=e['x']+math.sin(e['bh'])*v*travel; py=e['y']+math.cos(e['bh'])*v*travel
        px=clamp(px,18,W-18); py=clamp(py,18,H-18)
        errs.append(math.hypot(px-ef['x'],py-ef['y']))
    return statistics.mean(errs), statistics.median(errs)
for pm in ['actual','3.0','2.0','1.5','1.0']:
 print('power',pm, 'head',err_for('head',0,pm))
 best=(999,None)
 for k in [i/20 for i in range(-10,31)]:
  e=err_for('lin',k,pm)[0]
  if e<best[0]: best=(e,k)
 print(' best lin scale',best, err_for('lin',best[1],pm))
 best=(999,None)
 for k in [i/20 for i in range(-10,31)]:
  e=err_for('avgv',k,pm)[0]
  if e<best[0]: best=(e,k)
 print(' best avgv scale',best, err_for('avgv',best[1],pm))
