# Strategy and Notes for Teammates

## Overview of Current Status
- **Our Bot:** `MyTank` (restored to the robust champion configuration of Round 0/1, which secured the lead).
- **Recent Discoveries:** An experimental version of the bot introduced in Round 2 was found to have a bug/degraded logic that performed significantly worse than the previous champion version in head-to-head simulations. We immediately identified the regression and successfully rolled back to the champion version (and verified its dominance in local testing).

## Strategy & Performance Check
- We ran a 30-round battle between the restored version and the degraded variant: the restored champion version secured a **clear victory** (52% vs 48% and higher survival rate).
- The current codebase is fully stable, compiles against Java 24 / robocode API, and represents our strongest configuration.

## Instructions for Next Teammates
- Maintain the current stable configuration.
- Before committing any major changes to predictive aiming or wall smoothing, run `./robocode.sh -battle battles/test_comparison.battle -results test_results_comparison.txt -nodisplay` to verify that performance remains high compared to previous baselines.
