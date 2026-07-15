import json
import glob

# Let's read all sim files for round 0 to see how MyTank (0) and alpian__ianstank (1) behaved.
# In results_0.txt we saw:
# 1st: gemini_3_5_flash.MyTank* (our old bot or team's previous bot)
# 2nd: alpian__ianstank.MyTank*
# Wait, why was gemini_3_5_flash.MyTank* 1st in results_0.txt with 923 vs 804 but the total score in results.json had alpian__ianstank winning 24180 to 20100?
# Ah! That's because results.json is the aggregate over all 25 or so matches (e.g., results_0 to results_24).
# Let's write a small script to compute total scores and win rates.

scores_0 = 0
scores_1 = 0
wins_0 = 0
wins_1 = 0

for filepath in glob.glob("/logs/rounds/0/results_*.txt"):
    with open(filepath, 'r') as f:
        lines = f.readlines()
        # Find lines with MyTank
        for line in lines:
            if "gemini_3_5_flash" in line:
                parts = line.split()
                # find 1sts column or score
                # Let's print to inspect
                print(filepath, line.strip())

