// CodeClash ladder import
// Source: https://github.com/mcd8604/robocode/blob/HEAD/src/mcd/Hunter.java
// Author: mcd8604   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import java.awt.geom.Point2D;

import robocode.*;
import robocode.util.Utils;


public class MyTank extends AdvancedRobot {

	private double gunTurn;
	private double radarTurn;
	private double bulletSpeed;
	private double bulletPower;
	private double targetLastHeading;

	public void run() {
		gunTurn = 0;
		radarTurn = Math.toRadians(360);
		setAdjustRadarForGunTurn(true);
		setAdjustGunForRobotTurn(true);
		bulletPower = Rules.MAX_BULLET_POWER;
		bulletSpeed = Rules.getBulletSpeed(bulletPower);
		targetLastHeading = 0;

		while(true){
			setTurnGunRightRadians(gunTurn);
			setTurnRadarRightRadians(radarTurn);
			setAhead(10);
			setTurnRight(360);
			execute();
		}
	}

	public void onScannedRobot(ScannedRobotEvent e) {

		double h = getHeadingRadians();
		double r = getRadarHeadingRadians();
		double g = getGunHeadingRadians();

		double b = e.getBearingRadians();
		double bAbs = Utils.normalRelativeAngle(b + h);
		double eH = e.getHeadingRadians();
		double v = e.getVelocity();
		double d = e.getDistance();

		// predict next heading

		double dH = eH - targetLastHeading;
		double nextH = eH;
		if(dH <= Rules.MAX_TURN_RATE_RADIANS)
			nextH += dH;
		targetLastHeading = eH;

		// predict target's next location and (absolute) bearing

		double x = Math.cos(bAbs) * d;
		double y = Math.sin(bAbs) * d;
		double dX = Math.cos(nextH) * v;
		double dY = Math.sin(nextH) * v;
		double nX = x + dX;
		double nY = y + dY;
		double nextBAbs = Math.atan2(nY, nX);

		// set radar to target's next position

		radarTurn = Utils.normalRelativeAngle(nextBAbs - r);


		if(d > getHeight() * 4)
		{
			// if target is far enough (height * 4) away, "lead" the target
			// - find ideal predicted location

			double simD;
			double dT = 0, bT;

			do
			{
				simD = Point2D.Double.distance(0, 0, nX, nY);
				bT = simD / bulletSpeed;
				dT++;
				if(dH <= Rules.MAX_TURN_RATE_RADIANS)
					nextH += dH;
				dX = Math.cos(nextH) * v;
				dY = Math.sin(nextH) * v;
				nX += dX;
				nY += dY;
			}while(Math.abs(bT - dT) > 1);

		}

		nextBAbs = Math.atan2(nY, nX);
		gunTurn = Utils.normalRelativeAngle(nextBAbs - g);

		// if the gun is close enough (5 for now) to the required heading, shoot

		if(Math.toDegrees(gunTurn) <= 5)
		{
			setFire(bulletPower);
		}
	}

}
