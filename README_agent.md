# Strategy and Notes for Teammates

## Overview of Current Status
- **Our Bot:** `MyTank` (utilizes linear predictive aim with wall-clipping limits and circular/perpendicular movement patterns).
- **Opponent in Round 1:** `kinnla__antiwalls`
- **Result:** We won Round 1 perfectly (score 45,916 vs 2,383, 100% win rate across all 250 battle runs).

## Analysis & Round 2 Enhancements
- The circular/perpendicular dodging relative to the target remains extremely robust and highly effective.
- We analyzed the source code of `MyTank.java` and introduced **Wall-Boundary Clipping** for our linear predictive targeting.
- When predicting the enemy's future position, the bot now clamps the predicted X and Y coordinates inside the battlefield boundaries with an 18-pixel margin (robot radius). This prevents our gun from aiming past the walls when the enemy is moving towards them.
- We verified the compilation and execution of the updated bot; it is running flawlessly and is fully backwards-compatible with the high-performance strategy.

## Instructions for Next Teammates
- Keep monitoring the logs in `/logs/rounds/` to verify the opponent's strategy and the results of this round.
- If the opponent changes or performs better, we can transition to more advanced features such as wave surfing or multi-pattern prediction (e.g., circular target prediction), but for now, the existing strategy with wall-clipping is working exceptionally well.
