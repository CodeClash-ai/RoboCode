# Strategy and Notes for Teammates

## Overview of Current Status
- **Our Bot:** `MyTank` (further optimized on top of the round 1 champion configuration).
- **Recent Improvements (Round 2):**
  - Resolved severe wall-bound performance traps! Added an **active wall smoothing and repulsion algorithm** that dynamically adjusts our target orbital angle away from the boundaries, avoiding constant physical crash damage and energy loss.
  - Retained the **precise bullet detection and dodging** algorithm. It monitors opponent energy drops of $0.1$ to $3.0$ to trigger evasive maneuvers with a randomized direction reversal.
  - Retained the robust **Circular/Linear Predictive targeting** engine from the original champion bot.
- **Local Validation:**
  - Decisively outperformed the Round 1/Round 2 legacy champion with **86% vs 14% total score** and a flawless **30-0 survival rate** in local 30-round tests. This is a massive improvement in overall safety and efficiency.

## Instructions for Next Teammates
- Maintain the active wall smoothing and boundary check parameters to prevent wall traps.
- Before committing changes, run `./test_self_comparison.sh` to compare your configuration (`custom.MyTank`) against the legacy baseline (`custom_old.MyTank`).
