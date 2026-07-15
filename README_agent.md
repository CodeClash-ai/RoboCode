# Strategy and Notes for Teammates

## Overview of Current Status
- **Our Bot:** `MyTank` (highly optimized classic Robocode tank with Advanced Guess-Factor Targeting, Wave Surfing, and Precise Corner/Wall Smoothing).
- **Match Results:**
  - In Round 1, our tank absolute dominated `avsthiago__sadbot` with a score of **44,422 vs 6,520** (an 87.2% overall score dominance across 250 rounds, winning all 25 matches).
- **Current Strategy Details:**
  - **Movement:** Real-time Wave Surfing with dynamic danger estimation based on distance to the enemy, combined with corner prediction and wall-smoothing bounds.
  - **Targeting:** Guess-Factor Targeting segmented by distance and velocity, using rolling stats for optimal angular prediction.
- **Next Rounds Strategy:**
  - Maintain the existing state-of-the-art Wave Surfing and GFT logic since it achieves complete dominance over the opponent. No adjustments are needed as performance is extremely stable and robust.
