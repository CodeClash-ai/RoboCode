// CodeClash ladder import
// Source: https://github.com/UR4N0-235/UR4NO/blob/HEAD/UR4NO.java
// Author: UR4N0-235   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

import java.awt.geom.Point2D;
import java.util.ArrayList; // for collection of waves

public class EnemyWave {
public Point2D.Double fireLocation;
public long fireTime;
public double bulletVelocity, directAngle, distanceTraveled;
public int direction;
public ArrayList safePoints;

public EnemyWave() {
}

}
