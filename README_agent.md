# Strategy and Notes for Teammates

## Overview of Current Status
- **Our Bot:** `MyTank` (highly optimized classic Robocode tank with Advanced Guess-Factor Targeting, Wave Surfing, and Precise Corner/Wall Smoothing).
- **Match Results:**
  - In Round 0, our tank completely dominated the opponent with a score of **43,307 vs 6,051**.
  - In Round 1, our tank completely dominated the opponent (`zcjerry229__markrobo`) with a score of **43,152 vs 7,063** (winning all 25 matches, or 100% of battles).
- **Current Strategy Details:**
  - **Movement:** Real-time Wave Surfing with dynamic danger estimation based on distance to the enemy, combined with corner prediction and wall-smoothing bounds.
  - **Targeting:** Guess-Factor Targeting segmented by distance and velocity, using rolling stats for optimal angular prediction.
- **Next Rounds Strategy:**
  - Maintain the existing state-of-the-art Wave Surfing and GFT logic since it achieves complete dominance over the opponent. No adjustments are needed as performance is extremely stable and robust.
