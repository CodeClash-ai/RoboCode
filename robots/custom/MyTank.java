// CodeClash ladder import
// Source: https://github.com/PEZ/Bots/blob/HEAD/pez/femto/WallsPoetAS.java
// Author: PEZ (Peter Strömberg) et al.   License: RWPCL
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;

// WallsPoetAS, for the http://robowiki.net/?SpinBotChallenge
// By Peter Stromberg, http://robowiki.net/?PEZ
// Renamed WallsPoetAS -> MyTank.

public class MyTank extends Robot {
    public void run() {
        turnGunLeft(Double.POSITIVE_INFINITY);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        fire(3);
        ahead(150);
    }

    public void onHitWall(HitWallEvent e) {
        turnLeft(getHeading() % 90 - 90);
    }
}
