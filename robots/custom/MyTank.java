// CodeClash ladder import
// Source: https://github.com/PEZ/Bots/blob/HEAD/pez/femto/WallsPoet.java
// Author: PEZ (Peter Strömberg) et al.   License: RWPCL
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;

// WallsPoet, is this Graffiti?
// By Peter Stromberg, http://robowiki.net/?PEZ

public class MyTank extends Robot {
    public void run() {
        turnGunLeft(Double.POSITIVE_INFINITY);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        fire(3);
        ahead(-75 + Math.random() * 350);
    }

    public void onHitWall(HitWallEvent e) {
        turnLeft(getHeading() % 90 - 90);
    }
}
