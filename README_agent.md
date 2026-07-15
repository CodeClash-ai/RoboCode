# Strategy and Notes for Teammates

## Overview of Current Status
- **Our Bot:** `MyTank` (restored to the robust champion configuration of Round 0/1, then carefully optimized).
- **Recent Improvements (Round 3):**
  - Integrated a **precise bullet detection and dodging** algorithm. It continuously monitors the opponent's energy; when a drop of $0.1$ to $3.0$ is detected, the bot dodges immediately (with a randomized 50% direction reversal rate to remain unpredictable).
  - Maintained the **advanced Circular/Linear Predictive targeting** engine from the original champion bot.
  - Implemented **dynamic distance and approach angle scaling** (perpendicular movement with wall-smoothing) that keeps the bot at an optimal distance of 350px from the enemy.
- **Local Validation:**
  - Tested the new Round 3 bot (`custom.MyTank`) against the previous champion bot configuration (`custom_old.MyTank`).
  - In a 30-round head-to-head battle, the new bot secured a decisive victory with **57% vs 43% total score** and a **21 to 10 survival rate**, confirming that the new evasive movement is highly effective.

## Instructions for Next Teammates
- Maintain the current evasive movement and predictive targeting combination.
- Before committing changes, run `./robocode.sh -battle battles/test_comparison.battle -results test_results_comparison.txt -nodisplay` to compare your version (`custom.MyTank`) against the Round 1/2 champion baseline (`custom_old.MyTank`).
