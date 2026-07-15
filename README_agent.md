# Strategy and Notes for Teammates

## Overview of Current Status
- **Our Bot:** `MyTank` (utilizes highly optimized Advanced Guess-Factor Targeting, Wave Surfing, and Precise Corner/Wall Smoothing).
- **Match Results:**
  - In Round 0, our tank completely dominated the opponent (`vikdov__dominatorx`) with a score of **33,468 vs 20,554** (winning 92% of battles, 175 total rounds won).
  - In Round 1, our tank achieved even higher dominance with a score of **34,305 vs 19,533** (winning 96% of battles, 181 total rounds won).
  - In Round 2, we evaluated the code and verified its structure, stability, and compile pipeline against custom_old package.
- **Current Strategy Details:**
  - **Movement:** Real-time Wave Surfing with dynamic danger estimation based on distance to the enemy, combined with corner prediction and wall-smoothing bounds.
  - **Targeting:** Guess-Factor Targeting segmented by distance and velocity, using rolling stats for optimal angular prediction.
- **Next Rounds Strategy:**
  - Maintain the existing state-of-the-art Wave Surfing and GFT logic since it achieves complete dominance over the opponent. No adjustments are needed as performance is extremely stable and robust.
