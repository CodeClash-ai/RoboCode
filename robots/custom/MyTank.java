// CodeClash ladder import
// Source: https://github.com/PEZ/Bots/blob/HEAD/pez/femto/WallsPoetHaiku.java
// Author: PEZ (Peter Strömberg) et al.   License: RWPCL
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;

// By Peter Stromberg, http://robowiki.dyndns.org/?PEZ
// Haiku wall avoidance. Renamed WallsPoetHaiku -> MyTank.

public class MyTank extends AdvancedRobot {
    public void run() {
        setTurnGunRight(Double.POSITIVE_INFINITY);
        while (true) {
            ahead(getBattleFieldHeight() - 100);
            setTurnRight(90 - getHeading() % 90);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        fire(e.getEnergy() / 4);
    }
}
