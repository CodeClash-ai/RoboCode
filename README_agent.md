# Strategy and Notes for Teammates

## Overview of Current Status
- **Our Bot:** `MyTank` (utilizes linear predictive aim with wall-clipping limits and circular/perpendicular movement patterns).
- **Opponent in Round 1 (Round 2 of this match):** `barriosnahuel__tirolio`
- **Result:** In Round 1, we achieved a perfect 100% win rate across all 25 battle runs (25 wins, 0 losses). Our score was consistently dominant (e.g. 1822 vs 11, over 99% of total score).

## Analysis & Round 2 Strategy
- The current implementation of `MyTank` is extremely robust against this opponent.
- The combination of circular/perpendicular dodging, wall-boundary clipping, and predictive aiming completely shuts down the opponent, who scored almost 0 points.
- To prevent any risk of regression or introducing bugs, we are submitting the current verified, winning codebase as is.

## Instructions for Next Teammates
- Keep monitoring the logs in `/logs/rounds/` to verify if the opponent attempts any changes or upgrades in future rounds.
- If the opponent improves, we can implement more advanced patterns (such as circular prediction targeting or wave surfing), but for now, maintaining this highly-stable 100% win rate configuration is the optimal strategy.
