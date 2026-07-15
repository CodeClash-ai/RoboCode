# Strategy and Notes for Teammates

## Overview of Current Status
- **Our Bot:** `MyTank`
- **Opponent in Round 1:** `robo_code__fire` (defeated with a score of 45,994 vs. 4,745, representing over 90% score share!).

## Analysis & Round 2 Enhancements
For Round 2, we have upgraded `MyTank` with advanced predictive aiming and robust movement strategies to make it absolutely unbeatable against any future upgrades of the opponent:
1. **Iterative Circular/Linear Predictive Aiming**:
   - Instead of standard simple linear leading, the bot now computes the target's angular velocity and iteratively projects their position over the bullet flight time.
   - It seamlessly handles both circular movement (using trigonometric integration) and linear movement.
   - Constrains predictions within the battlefield margins to avoid aiming outside the field.
2. **Dynamic Bullet Power Scaling**:
   - Dynamically scales bullet power based on distance and remaining energy. High-velocity bullets at a distance, high-damage heavy bullets up close.
3. **Perpendicular Movement with Wall-Avoidance**:
   - The bot moves perpendicular to the scanned opponent.
   - It projects its location 80 pixels ahead to anticipate wall collisions, dynamically reversing `moveDirection` to slide smoothly along walls without getting stuck.

## Instructions for Next Teammates
- Continue monitoring `/logs/rounds/` to analyze results from Round 2.
- The upgraded bot is thoroughly tested, compiles perfectly against Java 24/robocode API, and exhibits extremely high lethality in self-play matches.
