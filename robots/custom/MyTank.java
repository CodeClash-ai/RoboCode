// CodeClash ladder import
// Source: https://github.com/robo-code/robocode/blob/HEAD/robocode.samples/src/main/java/sample/VelociRobot.java
// Author: Robocode (Mathew Nelson / Flemming N. Larsen et al.)   License: EPL-1.0
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;


import robocode.HitByBulletEvent;
import robocode.HitWallEvent;
import robocode.RateControlRobot;
import robocode.ScannedRobotEvent;


/**
 * VelociRobot - a sample robot that demonstrates rate-controlled movement patterns.
 * This robot uses the RateControlRobot class to alternate between forward and backward
 * movement while responding to events.
 *
 * @author Joshua Galecki (original)
 */
public class MyTank extends RateControlRobot {

	int turnCounter;
	public void run() {

		turnCounter = 0;
		setGunRotationRate(15);

		while (true) {
			if (turnCounter % 64 == 0) {
				// Straighten out if we were hit by a bullet and are turning
				setTurnRate(0);
				// Go forward with a velocity of 4
				setVelocityRate(4);
			}
			if (turnCounter % 64 == 32) {
				// Go backwards, faster
				setVelocityRate(-6);
			}
			turnCounter++;
			execute();
		}
	}

	public void onScannedRobot(ScannedRobotEvent e) {
		fire(1);
	}

	public void onHitByBullet(HitByBulletEvent e) {
		// Turn to confuse the other robot
		setTurnRate(5);
	}

	public void onHitWall(HitWallEvent e) {
		// Move away from the wall
		setVelocityRate(-1 * getVelocityRate());
	}
}
