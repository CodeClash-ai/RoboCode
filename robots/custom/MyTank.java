// CodeClash ladder import
// Source: https://github.com/PEZ/Bots/blob/HEAD/pez/femto/DroidPoet.java
// Author: PEZ (Peter Strömberg) et al.   License: RWPCL
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;

// By Peter Strmberg, http://robowiki.dyndns.org/?PEZ
// Blind and strong

public class MyTank extends Robot implements Droid {
    public void run() {
        while (true) {
            ahead(Math.random() * 600);
            turnGunRight(Math.toDegrees(Math.atan2(getBattleFieldWidth() / 2 - getX(), getBattleFieldHeight() / 2 - getY())) -
                getGunHeading());
            fire(1.2);
        }
    }

    public void onHitWall(HitWallEvent e) {
        turnRight(90 - getHeading() % 90);
    }
}
