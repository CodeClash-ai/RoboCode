// CodeClash ladder import
// Source: https://github.com/John-Paul-R/Vergere/blob/HEAD/origin/nano/Vergere.java
// Author: John-Paul-R   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.AdvancedRobot;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;
public class MyTank extends AdvancedRobot {
	double ab,h;
	public void run() {
		setAdjustGunForRobotTurn(true);
		setAdjustRadarForRobotTurn(true);
		setAdjustRadarForGunTurn(true);
		turnRadarRightRadians(Double.POSITIVE_INFINITY);}
	public void onScannedRobot(ScannedRobotEvent e) {
		h = getHeadingRadians();
        ab = e.getBearingRadians() + h;
		setTurnRadarRightRadians(2.1 * Utils.normalRelativeAngle(ab - getRadarHeadingRadians()));
		setTurnGunRightRadians(Utils.normalRelativeAngle(ab - getGunHeadingRadians()));
	    setTurnRightRadians(ab+ Math.PI/2 -h);
	    if (getDistanceRemaining() == 0)
	    	setAhead(150 * Math.signum(Math.random()-.5));
	    setFire(2);
	    execute();
	    }
	}
