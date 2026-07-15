# Agent Notes (Round 1)

## Current bot: robots/custom/MyTank.java
Rewrote the weak "TearsofSteel/Seesaw" starter into a competitive 1v1 AdvancedRobot:
- Radar: infinite-lock narrow sweep on the single enemy.
- Gun: iterative circular/linear predictive targeting; bullet power scaled by
  distance and remaining energy; only fires when gun is aligned & cool.
- Movement: orbital (perpendicular) motion around enemy with wall smoothing,
  random reversals, and dodge-on-enemy-fire (energy-drop detection).
- Reacts to onHitByBullet / onHitWall / onHitRobot with direction changes.

## CRITICAL BUILD NOTE
The default `javac` here is Java 24 -> class file major version 68, which
Robocode 1.10 CANNOT load ("Can't find 'custom.MyTank'").
ALWAYS compile targeting Java 8 bytecode:

    javac --release 8 -cp libs/robocode.jar -d robots robots/custom/MyTank.java

The starter MyTank.class was committed as Java 24 which would have failed to load.
Committed .class is now major version 52 (Java 8). Verify with:
    javap -v robots/custom/MyTank.class | grep "major version"   # want 52

## Local test harness (flaky)
`./robocode.sh` doesn't pass the required --add-opens flags for Java 24 and the
robot.database rebuild races with battle start, so local battles frequently print
"Can't find" and empty results. To attempt a local run:
    java -cp "libs/*" -DROBOTPATH=/workspace/robots \
      --add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED \
      --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
      --add-opens=java.desktop/javax.swing.text=ALL-UNNAMED \
      --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
      robocode.Robocode -battle <battle> -nodisplay -nosound -results /tmp/res.txt
Sample robots (sample.Corners etc.) are NOT present in this install, so build a
test opponent from git history if you want head-to-head numbers, e.g.:
    git show 810cb53:robots/custom/MyTank.java | sed 's/package custom;/package testbot;/;...'

## Opponent
Round 0 log was a solo sim (only opus_4_8 present); opponent identity unknown.
Assume a generic decent bot. Predictive gun + orbital movement should beat
simple movers/fixed-gun bots.

## Ideas for next teammate
- Add multiple gun modes (head-on / linear / circular) and pick best by hit stats.
- Wave surfing for movement (bigger win, more code).
- Tune bullet power vs. distance further.
