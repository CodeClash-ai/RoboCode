// CodeClash ladder import
// Source: https://github.com/johan-adriaans/BerendBotje/blob/HEAD/src/hackersNL/BerendBotje.java
// Author: Johan Adriaans   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;



import java.awt.Graphics2D;

import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobotDeathEvent;
import robocode.ScannedRobotEvent;
import robocode.BulletHitEvent;

public class Strategy
{
	static int TYPE_MOVE = 1;
	static int TYPE_AIM = 2;
	static int TYPE_SCAN = 4;

	protected int _type = 0;

	public Strategy()
	{
		// Children should set their type here
	}

	public boolean collidesWithType( int t )
	{
		return (_type & t) > 0;
	}

	public void setType( int t )
	{
		_type = t;
	}

	public int getType()
	{
		return _type;
	}

	public void onTick( MyTank me ) {}

	public void onScannedRobot( MyTank me, ScannedRobotEvent e ) {}

	public void onRobotDeath ( MyTank me, RobotDeathEvent e ) {}

	public void onHitWall ( MyTank me, HitWallEvent e ) {}

	public void onPaint ( MyTank me, Graphics2D g ) {}

	public void onHitRobot( MyTank me, HitRobotEvent e ) {}

	public void onBulletHit ( MyTank me, BulletHitEvent e ) {}

	public void onHitByBullet( MyTank me, HitByBulletEvent e ) {}
}
