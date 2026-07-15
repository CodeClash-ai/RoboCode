import json
import glob

# Let's count occurrences of "1st:" for both bots
gemini_firsts = 0
opponent_firsts = 0

for filepath in glob.glob("/logs/rounds/0/results_*.txt"):
    with open(filepath, 'r') as f:
        content = f.read()
        if "1st: gemini_3_5_flash" in content:
            gemini_firsts += 1
        elif "1st: alpian" in content:
            opponent_firsts += 1

print(f"Gemini 1sts: {gemini_firsts}, Opponent 1sts: {opponent_firsts}")
