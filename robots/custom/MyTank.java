// CodeClash ladder import
// Source: https://github.com/alpian/robocode/blob/HEAD/src/main/java/com/github/alpian/robocode/tanks/IansTank.java
// Author: alpian   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import robocode.Robot;
import robocode.ScannedRobotEvent;

public class MyTank extends Robot {
    public void run() {
        while (true) {
            ahead(100);
            turnGunRight(360);
            back(100);
            turnGunRight(360);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        fire(1);
    }
}
