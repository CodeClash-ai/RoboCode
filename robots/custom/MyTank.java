// CodeClash ladder import
// Source: https://github.com/iagomonteiro13579/robocode.NPC/blob/HEAD/npc/NPCSniperBot.java
// Author: iagomonteiro13579   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import robocode.*;
import robocode.util.Utils; // <-- Import necessário para corrigir o erro
import java.awt.*;

public class MyTank extends AdvancedRobot {
    private ScannedRobotEvent currentTarget = null;

    public void run() {
        setColors(Color.black, Color.green, Color.red);

        setAdjustRadarForGunTurn(true);
        setAdjustGunForRobotTurn(true);

        while (true) {
            turnRadarRight(360); // Radar gira o tempo todo
            execute();
            doRandomMovement();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        if (currentTarget == null || e.getEnergy() < currentTarget.getEnergy()) {
            currentTarget = e;
        }

        if (e.getName().equals(currentTarget.getName())) {
            double absoluteBearing = getHeadingRadians() + e.getBearingRadians();
            double gunTurn = Utils.normalRelativeAngle(absoluteBearing - getGunHeadingRadians());
            setTurnGunRightRadians(gunTurn);

            double distance = e.getDistance();
            double firePower = Math.min(3, Math.max(1.1, 400 / distance));

            if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
                fire(firePower);
            }
        }
    }

    public void doRandomMovement() {
        if (getDistanceRemaining() == 0 && getTurnRemaining() == 0) {
            double angle = (Math.random() * 180) - 90;
            double distance = (Math.random() * 150) + 50;
            setTurnRight(angle);
            setAhead(distance);
        }
    }

    public void onHitWall(HitWallEvent e) {
        back(100);
        turnRight(90);
    }

    public void onHitRobot(HitRobotEvent e) {
        back(50);
    }

    public void onRobotDeath(RobotDeathEvent e) {
        if (currentTarget != null && e.getName().equals(currentTarget.getName())) {
            currentTarget = null;
        }
    }
}
