import os
import glob

def analyze_round(round_num):
    print(f"=== ROUND {round_num} ===")
    g_score, o_score = 0, 0
    g_wins, o_wins = 0, 0
    g_survival_wins, o_survival_wins = 0, 0
    
    files = glob.glob(f"/logs/rounds/{round_num}/results_*.txt")
    if not files:
        print("No files found.")
        return
        
    for filepath in sorted(files, key=lambda x: int(x.split('_')[-1].split('.')[0])):
        with open(filepath, 'r') as f:
            lines = [line.strip() for line in f.readlines() if "MyTank" in line]
        
        # Determine who is gemini and who is the opponent
        gemini_line = None
        opponent_line = None
        for line in lines:
            if "gemini_3_5_flash" in line:
                gemini_line = line
            else:
                opponent_line = line
                
        if gemini_line and opponent_line:
            # Format is typically:
            # Rank Name Score (Percentage) Survival SurvivalBonus BulletDamage ...
            # 1st: gemini_3_5_flash.MyTank*	923 (53%)	200	40	597	87	0	0	4	6	0
            g_parts = gemini_line.split('\t')
            o_parts = opponent_line.split('\t')
            
            g_rank_part = g_parts[0].split()[0] # e.g. "1st:" or "2nd:"
            o_rank_part = o_parts[0].split()[0]
            
            g_pts = int(g_parts[1].split()[0])
            o_pts = int(o_parts[1].split()[0])
            
            g_score += g_pts
            o_score += o_pts
            
            # Survival round wins are usually the 9th column (e.g. 4 vs 6)
            # Let's count who got 1st place in the match (the rank part)
            if "1st:" in g_rank_part:
                g_wins += 1
            else:
                o_wins += 1
                
            # Let's extract the actual rounds won (usually the 9th column / index 8 in parts)
            try:
                g_round_wins = int(g_parts[8])
                o_round_wins = int(o_parts[8])
                g_survival_wins += g_round_wins
                o_survival_wins += o_round_wins
            except Exception as e:
                pass
                
    total_matches = len(files)
    print(f"Total Matches: {total_matches}")
    print(f"Gemini Total Score: {g_score} | Opponent Total Score: {o_score}")
    print(f"Gemini Matches Won: {g_wins} ({g_wins/total_matches*100:.1f}%) | Opponent Matches Won: {o_wins} ({o_wins/total_matches*100:.1f}%)")
    print(f"Gemini Total Rounds Won: {g_survival_wins} | Opponent Total Rounds Won: {o_survival_wins}")

analyze_round(0)
analyze_round(1)
analyze_round(2)
