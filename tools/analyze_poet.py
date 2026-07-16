#!/usr/bin/env python3
"""Quick summary for pez__poet Robocode JSONL traces.

Usage:
  python3 tools/analyze_poet.py /logs/rounds/1
  python3 tools/analyze_poet.py '/logs/rounds/1/sim_*.jsonl'

Prints live W/L/D, range/energy/fire summaries, and result-file score
components when a round directory is provided.
"""
import glob, json, math, os, re, statistics, sys


def mean(xs):
    return statistics.mean(xs) if xs else float('nan')


def med(xs):
    return statistics.median(xs) if xs else float('nan')


def pct(xs, p):
    if not xs:
        return float('nan')
    xs = sorted(xs)
    return xs[min(len(xs) - 1, max(0, int(round((len(xs) - 1) * p))))]


def files_from_arg(arg):
    if os.path.isdir(arg):
        return sorted(glob.glob(os.path.join(arg, 'sim_*.jsonl')))
    return sorted(glob.glob(arg))


def summarize_results(arg):
    if not os.path.isdir(arg):
        return
    rows = {'gpt': [], 'opp': []}
    for path in glob.glob(os.path.join(arg, 'results_*.txt')):
        for line in open(path, errors='ignore'):
            if 'gpt_5_5' not in line and 'pez__poet' not in line:
                continue
            parts = line.strip().split('\t')
            if len(parts) < 9:
                continue
            vals = [int(parts[1].split()[0])] + [int(x) for x in parts[2:9]]
            rows['gpt' if 'gpt_5_5' in line else 'opp'].append(vals)
    if not rows['gpt']:
        return
    keys = ['total', 'surv', 'survBonus', 'bulletDmg', 'bulletBonus', 'ram2', 'ramBonus', 'firsts']
    print('Result-file averages per 10-round battle:')
    for who in ('gpt', 'opp'):
        print(' ', who, {k: round(mean([r[i] for r in rows[who]]), 1) for i, k in enumerate(keys)})


def analyze(files):
    wins = losses = draws = 0
    lens = []
    my_end = []
    opp_end = []
    my_min = []
    dists = []
    close180 = close250 = 0
    wall_ticks = ticks = 0
    speeds = []
    turns = []
    drops = []
    loss_snapshots = []
    for path in files:
        lines = [line for line in open(path, errors='ignore').read().splitlines() if line.strip()]
        if not lines:
            continue
        meta = json.loads(lines[0])
        robots = meta.get('robots', {})
        my_ids = [rid for rid, name in robots.items() if 'gpt' in name]
        if not my_ids:
            continue
        my_id = my_ids[0]
        opp_id = [rid for rid in robots if rid != my_id][0]
        prev = None
        last_state = None
        winner = None
        draw = False
        min_e = 999.0
        for line in lines[1:]:
            event = json.loads(line)
            if 'winner' in event:
                winner = event.get('winner')
                draw = bool(event.get('draw'))
                continue
            units = {str(u['i']): u for u in event.get('u', [])}
            if my_id not in units or opp_id not in units:
                continue
            me = units[my_id]
            op = units[opp_id]
            last_state = (event['t'], me, op)
            min_e = min(min_e, me['e'])
            dist = math.hypot(me['x'] - op['x'], me['y'] - op['y'])
            dists.append(dist)
            close180 += dist < 180.0
            close250 += dist < 250.0
            wall_ticks += op['x'] < 50 or op['x'] > meta.get('w', 800) - 50 or op['y'] < 50 or op['y'] > meta.get('h', 600) - 50
            ticks += 1
            speeds.append(abs(op['v']))
            if prev:
                pop = prev[opp_id]
                dh = abs((op['bh'] - pop['bh'] + math.pi) % (2 * math.pi) - math.pi)
                turns.append(dh)
                de = pop['e'] - op['e']
                if 0.09 < de <= 3.01:
                    drops.append(de)
            prev = units
        if not last_state:
            continue
        t, me, op = last_state
        lens.append(t)
        my_end.append(me['e'])
        opp_end.append(op['e'])
        my_min.append(min_e)
        if draw or winner is None:
            draws += 1
        elif 'gpt' in winner:
            wins += 1
        else:
            losses += 1
            loss_snapshots.append((os.path.basename(path), t, round(me['e'], 1), round(op['e'], 1), round(math.hypot(me['x'] - op['x'], me['y'] - op['y']), 1)))
    print(f'Traces: {len(files)}  W/L/D: {wins}/{losses}/{draws}')
    print(f'Length avg {mean(lens):.1f}; our end/min energy avg {mean(my_end):.1f}/{mean(my_min):.1f}; opp end avg {mean(opp_end):.1f}')
    print(f'Distance avg/med/p90 {mean(dists):.1f}/{med(dists):.1f}/{pct(dists, .9):.1f}; close<180 {close180/max(1,ticks):.3f}, close<250 {close250/max(1,ticks):.3f}')
    print(f'Opponent speed avg/med {mean(speeds):.2f}/{med(speeds):.2f}; turn avg/med {mean(turns):.4f}/{med(turns):.4f}; wall frac {wall_ticks/max(1,ticks):.3f}')
    print(f'Enemy fire drops n={len(drops)} avg/med/p90 {mean(drops):.2f}/{med(drops):.2f}/{pct(drops, .9):.2f}')
    if loss_snapshots:
        print('Loss snapshots:', loss_snapshots[:20])


if __name__ == '__main__':
    arg = sys.argv[1] if len(sys.argv) > 1 else '/logs/rounds/1'
    summarize_results(arg)
    analyze(files_from_arg(arg))
