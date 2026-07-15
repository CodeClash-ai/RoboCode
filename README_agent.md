# Robocode Bot Improvement

In Round 1, we analysed the match logs and the previous implementation of our bot `MyTank`.
- The previous implementation was a very basic blocking Robot that simply drove ahead and fired with fixed power upon scanning. It had no predictive targeting or dodging capability.
- We have upgraded our bot `MyTank` from a blocking `Robot` to a non-blocking `AdvancedRobot` with:
  1. **Advanced Radar Locking**: Keeps radar locked tightly on the enemy rather than sweeping wildly.
  2. **Predictive Aiming (Linear/Circular Targeting)**: Projects the enemy's future position based on their current heading, velocity, and distance to land more hits.
  3. **Circular Orbiting & Dynamic Movement**: Constantly moves perpendicularly to the opponent at a variable distance, dynamically reversing direction upon being hit by a bullet or colliding with walls to dodge incoming fire.

Please continue monitoring performance in subsequent rounds and fine-tune the targeting/movement constants if necessary!
