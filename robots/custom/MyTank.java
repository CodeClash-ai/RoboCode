// CodeClash ladder import
// Source: https://github.com/robo-code/robocode/blob/HEAD/robocode.samples/src/main/java/sampleex/ProxyOfGreyEminence.java
// Author: Robocode (Mathew Nelson / Flemming N. Larsen et al.)   License: EPL-1.0
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.ScannedRobotEvent;

// Ported from the Robocode sampleex chain (robo-code/robocode, author Pavel Savara):
// ProxyOfGreyEminence -> GreyEminence -> RegullarMonk. RegullarMonk is the abstract
// "infrastructure" base class; the actual runnable bot is the proxy that delegates all
// behaviour to a GreyEminence (which is a RegullarMonk). Renamed proxy -> MyTank.

public class MyTank extends AdvancedRobot {
    private final GreyEminence monk;

    public MyTank() {
        monk = new GreyEminence(this);
    }

    public void onHitByBullet(HitByBulletEvent event) {
        monk.onHitByBullet(event);
    }

    public void onScannedRobot(ScannedRobotEvent event) {
        monk.onScannedRobot(event);
    }

    public void run() {
        monk.run();
    }
}

// The power behind the throne.
class GreyEminence extends RegullarMonk {
    private final MyTank proxy;

    public GreyEminence(MyTank proxy) {
        this.proxy = proxy;
    }

    public void run() {
        while (true) {
            proxy.ahead(100); // Move ahead 100
            proxy.turnGunRight(360); // Spin gun around
            proxy.back(100); // Move back 100
            proxy.turnGunRight(360); // Spin gun around
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        proxy.fire(1);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        proxy.turnLeft(90 - e.getBearing());
    }
}

// Monk of an order. The infrastructure base class.
abstract class RegullarMonk {}
