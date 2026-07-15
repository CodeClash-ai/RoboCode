import json

with open('/logs/rounds/0/sim_0.jsonl', 'r') as f:
    for line in f:
        data = json.loads(line)
        if "u" in data:
            for robot in data["u"]:
                # index 1 is gemini_3_5_flash (our old bot from round 0/1)
                if robot["i"] == 1 and "s" in robot and robot["s"] != "ACTIVE":
                    print(f"Time {data['t']}: status={robot['s']}, x={robot['x']:.1f}, y={robot['y']:.1f}, e={robot['e']:.1f}")
