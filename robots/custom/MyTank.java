// CodeClash ladder import
// Source: https://github.com/sacdalance/robrrrat/blob/HEAD/RobrrRat.java
// Author: sacdalance   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import java.util.*;
import robocode.*;
import robocode.util.*;
import java.awt.Color;

// API help : https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html

//RobrrRat - a robot by Lance Sacdalan and Sophia Young

public class MyTank extends AdvancedRobot {
	// Random value for unpredictive movement
	private Random random = new Random();

	public void run() {

		setColors(Color.black,Color.white,Color.red); // body,gun,radar

		// Robot main loop
		while(true) {
			// Continuous scanning in all directions
			turnGunRight(360);
		}
	}

	public void onScannedRobot(ScannedRobotEvent e) {
		// Variables needed
		double distance = e.getDistance();
		// For unpredictive movement
		setAhead(random.nextDouble() * 360.0);
		setTurnRight(random.nextDouble() * 360.0);

		 // Aims to increase bullet damage input and survivability in different range of combat
		if (distance < 270) {
			// To acquire the enemy position
			double direction = getHeadingRadians() + e.getBearingRadians();
			// Formula for targeting (normalize bearing)
			setTurnGunRightRadians(Utils.normalRelativeAngle(direction - getGunHeadingRadians()));
			setFire(3);
		}
		else { // Momentary switch of input
			// Go near target
			setTurnRight(e.getBearing());
			// Maintain agility of robot
			setFire(Math.min(3, getEnergy() / 2));
		}
		// Execute other actions
		execute();
	}

    public void onHitWall(HitWallEvent e) {
        // Go near target
		setTurnRight(e.getBearing() + 90);
    }
}
