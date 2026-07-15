import json
import glob

files = glob.glob("/logs/rounds/0/results_*.txt")
our_wins = 0
opp_wins = 0
total_battles = 0

for f in files:
    total_battles += 1
    with open(f, 'r') as fh:
        lines = fh.readlines()
        if len(lines) >= 3:
            # 1st place
            if "gemini_3_5_flash" in lines[2]:
                our_wins += 1
            else:
                opp_wins += 1

print(f"Total battle runs analyzed: {total_battles}")
print(f"Our wins: {our_wins}, Opponent wins: {opp_wins}")
