// CodeClash ladder import
// Source: https://github.com/barriosnahuel/algorithms-robocode-kenchu/blob/HEAD/src/main/java/pnt/TiroLioYCoshaGolda3.java
// Author: barriosnahuel   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import robocode.ScannedRobotEvent;

/**
 * TODO : Javadoc for
 * <p/>
 * Created on 13/08/13, at 12:47.
 *
 * @author Nahuel Barrios <barrios.nahuel@gmail.com>.
 * @since 26.
 */
public class MyTank extends robocode.Robot {


    @Override
    public void run() {

        //noinspection InfiniteLoopStatement
        while (true) {
            ahead(100);
            turnRight(20);
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
//        fire(1);
    }
}
