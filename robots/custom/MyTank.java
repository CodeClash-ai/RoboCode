// CodeClash ladder import
// Source: https://github.com/PEZ/Bots/blob/HEAD/pez/femto/SmallPoet.java
// Author: PEZ (Peter Strömberg) et al.   License: RWPCL
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;

// MyTank, by PEZ

public class MyTank extends AdvancedRobot {
    public void run() { 
        setTurnGunRight(Double.POSITIVE_INFINITY); 
    } 

    public void onScannedRobot(ScannedRobotEvent e) { 
        setTurnRight(90 + e.getBearing());
        setAhead(200 * Math.sin(getTime())); 
        fire(3); 
    } 
}
