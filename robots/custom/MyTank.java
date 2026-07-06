// CodeClash ladder import
// Source: https://github.com/zcjerry229/RoboCode/blob/HEAD/src/MarkRobo/MarkRobo.java
// Author: zcjerry229   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import static robocode.util.Utils.normalRelativeAngleDegrees;
import java.awt.Color;
import robocode.*;

/**
 * Tracker - a sample robot by Mathew Nelson, and maintained by Flemming N. Larsen
 * <p/>
 * Locks onto a robot, moves close, fires when close.
 */
public class MyTank extends AdvancedRobot {
	int count = 0; // Keeps track of how long we've
	// been searching for our target
	double gunTurnAngle; // How much to turn our gun when searching
	double turnAngle;
	/**
	 * run:  Tracker's main run function
	 */
	public void run() {
		// Set colors

		setBodyColor(new Color(128, 128, 50));
		setGunColor(new Color(50, 50, 20));
		setRadarColor(new Color(200, 200, 70));
		setScanColor(Color.white);
		setBulletColor(Color.yellow);
		setAdjustGunForRobotTurn(true); // Keep the gun still when we turn
		gunTurnAngle = 10; // Initialize gunTurn to 10
		// Loop forever
		while (true) {

			// turn the Gun (looks for enemy)
			turnGunRight(10);
			// Keep track of how long we've been looking
			//count++;
			// If we've haven't seen our target for 3 turns, look left
			/*if (count > 3) {
				turnGunRight(-30);
				gunTurnAmt = -10;
			}*/
		}
	}
	/**
	 * onScannedRobot:  Here's the good stuff
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
		//setInterruptible(true);
		//count = 0;
		// Calculate exact location of the robot
		double absoluteBearing = getHeading() + e.getBearing();
		double bearingFromGun = normalRelativeAngleDegrees(absoluteBearing - getGunHeading());
		// If it's close enough, fire!
		if (Math.abs(bearingFromGun) <= 3) {

			turnGunRight(bearingFromGun);
			// We check gun heat here, because calling fire()
			// uses a turn, which could cause us to lose track
			// of the other robot.
			if (getGunHeat() == 0) {
				fire(Math.min(3 - Math.abs(bearingFromGun), getEnergy() - .1));
				ahead(50);
				turnGunRight(-bearingFromGun);
			}
		} // otherwise just set the gun to turn.
		// Note:  This will have no effect until we call scan()
		else {
			turnGunRight(bearingFromGun);
		}
		// Generates another scan event if we see a robot.
		// We only need to call this if the gun (and therefore radar)
		// are not turning.  Otherwise, scan is called automatically.
		if (bearingFromGun == 0) {
			fire(Math.min(3 - Math.abs(bearingFromGun), getEnergy() - .1));
			scan();
		}
	}
	/**
	 * onHitRobot:  Set him as our new target
	 */
	public void onHitRobot(HitRobotEvent e) {
		// Back up a bit.
		// Note:  We won't get scan events while we're doing this!
		// An AdvancedRobot might use setBack(); execute();
		gunTurnAngle = normalRelativeAngleDegrees(e.getBearing() + (getHeading() - getRadarHeading()));
		turnGunRight(gunTurnAngle);
		fire(3);
		back(50);
	}
	public void onHitByBullet(HitByBulletEvent e){
		if(e.getBearing() % 180 == 0){
			turnRight(e.getBearing() + 90);
			ahead(50);
		}
		else if(e.getBearing()>0){
			if(e.getBearing() < 30 || e.getBearing() > 150){
				turnAngle = 60;
			}else {
				turnAngle = 0;
			}
			turnRight(turnAngle);
			ahead(50);
			turnGunLeft(5);
		}
		else {
			if(e.getBearing() > -30 || e.getBearing() < -150){
				turnAngle = 60;
			}else {
				turnAngle = 0;
			}
			turnLeft(turnAngle);
			ahead(50);
			turnGunRight(5);
		}
		scan();
	}

	public void onHitWall(HitWallEvent e){
		fire(1);
		turnRight(e.getBearing()-180);
		ahead(50);
		scan();
	}

	/**
	 * onWin:  Do a victory dance
	 */
	public void onWin(WinEvent e) {
		for (int i = 0; i < 50; i++) {
			turnRight(30);
			turnLeft(30);
		}
	}
}
