// CodeClash ladder import
// Source: https://github.com/MiradoConsulting/roleksii/blob/HEAD/src/main/java/ROLEKSII.java
// Author: MiradoConsulting   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import robocode.*;
import robocode.util.Utils;

// CircleBot - a robot that moves in a big circle and shoots enemies
public class MyTank extends AdvancedRobot {

    // run method - main method for the robot
    public void run() {
        // Set the radar to turn right infinitely
        setTurnRadarRight(Double.POSITIVE_INFINITY);

        // Start moving in a big circle
        while (true) {
            // Move forward
            ahead(100);

            // Turn right 10 degrees
            turnRight(10);
        }
    }

    // onScannedRobot method - called when an enemy is detected
    public void onScannedRobot(ScannedRobotEvent e) {
        // Calculate the angle to the enemy
        double angleToEnemy = getHeading() + e.getBearing();

        // Calculate the gun turn angle to face the enemy
        double gunTurnAngle = Utils.normalRelativeAngleDegrees(angleToEnemy - getGunHeading());

        // Turn the gun to face the enemy
        turnGunRight(gunTurnAngle);

        // Fire at the enemy with maximum power
        fire(3);
    }
}
