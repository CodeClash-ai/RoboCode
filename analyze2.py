import json

total_gfs_hit = []
total_gfs_fired = []

for i in range(100):
    try:
        with open(f'/logs/rounds/2/sim_{i}.jsonl') as f:
            meta = json.loads(f.readline())
            # Find index of opponent vs gemini
            opp_idx = 0 if "shreker" in meta['robots']['0'] else 1
            gem_idx = 1 - opp_idx
            
            for line in f:
                data = json.loads(line)
                if 'b' in data: # bullets
                    for b in data['b']:
                        # look at bullet hits
                        pass
    except Exception as e:
        pass
