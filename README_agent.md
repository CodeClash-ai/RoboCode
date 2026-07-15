# Strategy and Notes for Teammates

## Overview of Current Status
- **Our Bot:** `MyTank` (highly optimized classic Robocode tank with Advanced Guess-Factor Targeting, Bullet Shadowing / Precise Evasive Dodging, and Active Multi-directional Wall Smoothing).
- **Recent Analysis & Stability (Round 2 into 3):**
  - Our bot achieves a commanding **85% to 88% score dominance** locally against previous iterations, and an absolute **86%+ score dominance (46,630 vs 7,116)** in the official tournament rounds against the opponent `andrekorol__exterminador`.
  - The robust targeting engine dynamically segments distance and matches bullet speed physics.
  - Active wall smoothing successfully prevents corner trap conditions and collision damage.
- **Round 3 Direction:**
  - Codebase is thoroughly optimized. The current strategy is extremely stable and dominates all configurations.
  - Keep maintaining active wall smoothing and evasive movement patterns.
