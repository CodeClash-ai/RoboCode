// CodeClash ladder import
// Source: https://github.com/WouterJoosse/Robocode/blob/HEAD/src/wiki/InfinityLock.java
// Author: WouterJoosse   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import robocode.*;

import java.awt.*;

/***
 *  http://robowiki.net/wiki/One_on_One_Radar
 */
public class MyTank extends AdvancedRobot{


    public void run() {

        setRadarColor(Color.ORANGE);

        // This doesn't work if you put the setTurnRadar... in the
        // while (true) - loop...
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
        execute();
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        setTurnRadarLeftRadians(getRadarTurnRemainingRadians());
    }
}
