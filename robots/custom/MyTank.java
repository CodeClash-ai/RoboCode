// CodeClash ladder import
// Source: https://github.com/PEZ/Bots/blob/HEAD/pez/femto/HaikuWalls.java
// Author: PEZ (Peter Strömberg) et al.   License: RWPCL
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;

// HaikuWalls, by tobe

public class MyTank extends AdvancedRobot {
    public void run() {
        setTurnGunRight(Double.POSITIVE_INFINITY);
    }
    public void onScannedRobot(ScannedRobotEvent e) {
        setAhead(Double.POSITIVE_INFINITY);
        fire( 3);
    }
    public void onHitWall(HitWallEvent e) {
        turnRight( 90 +e.getBearing());
    }
}
