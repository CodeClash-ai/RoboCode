// CodeClash ladder import
// Source: https://github.com/LoganCSC/robocode-robots/blob/HEAD/src/com/barrybecker4/DodgeBot2.java
// Author: LoganCSC   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import robocode.AdvancedRobot;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;

/**
 * derived from http://www.ibm.com/developerworks/library/j-dodge/
 */
public class MyTank extends AdvancedRobot  {

    private static final int TURN_AMOUNT = 2880;
    private double previousEnergy = 100;
    private int movementDirection = 1;
    private int gunDirection = 1;


    public void run() {
        setTurnGunRight(TURN_AMOUNT);

        while (true) {
            if (Math.random() < 0.01)
                ahead(Math.random() * 10.0 - 5.0);
        }

    }

    public void onScannedRobot(ScannedRobotEvent e) {

        // Stay at roughly right angles to the opponent
        setTurnRight(e.getBearing() + 90 -  (1 + Math.random()) * 10.0 * movementDirection);

        // If the bot has small energy drop, assume it fired
        dodgeIfEnemyFired(e);

        // When a bot is spotted, sweep the gun and radar in opposite direction
        gunDirection = -gunDirection;
        setTurnGunRight(TURN_AMOUNT * gunDirection);

        // Fire directly at target
        fire(determineFirepower(e.getDistance()));
    }

	/**
	 * onHitWall: What to do when you hit a wall
	 */
	public void onHitWall(HitWallEvent e) {
		setTurnLeft(100);
		ahead(40);
	}


	/**
	 * onHitRobot:  If it's our fault, we'll stop turning and moving,
	 * so we need to turn again to keep spinning.
	 */
	public void onHitRobot(HitRobotEvent e) {
		if (e.getBearing() > -10 && e.getBearing() < 10) {
			fire(2);
		}
		if (e.isMyFault()) {
			turnRight(5);
		}
	}


    private double determineFirepower(double distance) {
        double fp =  Math.min(3.0, 500.0 / (distance + 80.0));
        return fp;
    }

    private void dodgeIfEnemyFired(ScannedRobotEvent e) {
        double changeInEnergy = previousEnergy - e.getEnergy();
        if (changeInEnergy > 0 && changeInEnergy <= 3) {
            // Dodge!
            movementDirection = -movementDirection;
            setAhead((e.getDistance() / 4 + 35) * movementDirection);
        }
        previousEnergy = e.getEnergy();
    }
}
