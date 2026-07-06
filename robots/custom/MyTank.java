// CodeClash ladder import
// Source: https://github.com/Team488/RobocodeRobots/blob/HEAD/Robots/src/YYK/Meow.java
// Author: Team488   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.HitRobotEvent;
import robocode.Robot;
import robocode.ScannedRobotEvent;


public class MyTank extends AdvancedRobot{

	public void run()
	{
		while (true)
		{
		setTurnLeft(50);
		setAhead(15);
		System.out.println("My x is ");
		System.out.println("my y is ");
		execute();
		}
	}
	public void onScannedRobot(ScannedRobotEvent e) {
	    //setTurnRight(40);
		//setTurnRight(10);
		setFire(1);

	        //ahead(100);
		execute();

	}
		public void onHitRobot(HitRobotEvent event) {
		       if (event.getBearing() > -90 && event.getBearing() <= 90) {
		         back(100);

		      // Set the gun to turn 180 degrees to the left
		         setTurnGunLeft(180);

		         // Set the gun to turn 90 degrees to the right instead of left
		         // (overrides the previous order)
		         setTurnGunLeft(-90);


		         // Executes the last setTurnGunLeft()
		         execute();

		       }




		}
	}
