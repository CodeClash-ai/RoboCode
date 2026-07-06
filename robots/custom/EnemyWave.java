// CodeClash ladder import
// Source: https://github.com/JonHarder/RoboCodeCompetition/blob/HEAD/src/starterbot/StarterBot.java
// Author: JonHarder   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

import java.awt.geom.*; // for Point2D's

public class EnemyWave {
    Point2D.Double fireLocation;
    long fireTime;
    double bulletVelocity, directAngle, distanceTraveled;
    int direction;

    public EnemyWave() {
    }
}
