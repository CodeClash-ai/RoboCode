// CodeClash ladder import
// Source: https://github.com/PEZ/Bots/blob/HEAD/pez/femto/HaikuPoet.java
// Author: PEZ (Peter Strömberg) et al.   License: RWPCL
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;

// By Peter Stršmberg, http://robowiki.dyndns.org/?PEZ

public class MyTank extends AdvancedRobot {
    public void run() { 
        setTurnGunRightRadians(Double.POSITIVE_INFINITY); 
    } 

    public void onScannedRobot(ScannedRobotEvent e) { 
        setTurnRight(e.getBearing() + 80);
        setAhead((setFireBullet(2.1) == null ? 100 : 0) * Math.sin(getTime() / 12)); 
        setTurnGunLeftRadians(getGunTurnRemainingRadians());
    } 
} 
