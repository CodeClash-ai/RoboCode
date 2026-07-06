// CodeClash ladder import
// Source: https://github.com/rafaeljdesa/robocode/blob/HEAD/Ultron.java
// Author: rafaeljdesa   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;
import java.awt.Color;

// API help : http://robocode.sourceforge.net/docs/robocode/robocode/Robot.html

/**
 * Ultron - a robot by (Rafael de Sá)
 */
public class MyTank extends AdvancedRobot
{
	/**
	 * run: Ultron's default behavior
	 */
	public void run() {
		// Initialization of the robot should be put here

		setColors(Color.darkGray,Color.black,Color.red); // body,gun,radar
		setBulletColor(Color.red);

		// Robot main loop
		while(true) {
			ahead(80);
			back(80);
			turnGunRight(360);
		}
	}

	/**
	 * onScannedRobot: What to do when you see another robot
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
		for(int i=0; i<3; i++){
			fire(3);
		}
	}

	/**
	 * onHitByBullet: What to do when you're hit by a bullet
	 */
	public void onHitByBullet(HitByBulletEvent e) {
		setBack(80);
		setTurnRight(90);
		execute();
	}

	/**
	 * onHitWall: What to do when you hit a wall
	 */
	public void onHitWall(HitWallEvent e) {
		back(50);
		setBack(80);
		setTurnRight(90);
		execute();
	}
}
