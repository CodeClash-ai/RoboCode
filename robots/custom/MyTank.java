// CodeClash ladder import
// Source: https://github.com/PEZ/Bots/blob/HEAD/pez/femto/Poet.java
// Author: PEZ (Peter Strömberg) et al.   License: RWPCL
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;
import java.awt.geom.Point2D;

// Poet, just for fun
// By Peter Strmberg, http://robowiki.dyndns.org/?PEZ

public class MyTank extends AdvancedRobot {
    public void run() {
       turnGunRightRadians(Double.POSITIVE_INFINITY);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        setTurnRight(Point2D.Double.distance(400, 300, getX(), getY()) - 150);
        setAhead(100);
        setFire(3);
        setTurnGunLeftRadians(getGunTurnRemainingRadians());
    }
}
