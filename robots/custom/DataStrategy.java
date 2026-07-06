// CodeClash ladder import
// Source: https://github.com/johan-adriaans/BerendBotje/blob/HEAD/src/hackersNL/BerendBotje.java
// Author: Johan Adriaans   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

import java.util.EmptyStackException;

import robocode.BulletHitEvent;
import robocode.HitByBulletEvent;
import robocode.RobotDeathEvent;

public class DataStrategy extends Strategy
{
	/**
	 * Remove bot from enemyStack
	 */
	public void onRobotDeath( MyTank me, RobotDeathEvent e )
	{
		me.getData().removeEnemy( e.getName() );
		super.onRobotDeath( me, e );
	}

	public void onBulletHit( MyTank me, BulletHitEvent e )
	{
		// Get enemy from datacontainer and add hit
		try {
			Enemy enemy = me.getData().getEnemy( e.getName() );
			enemy.addHit(); // Update hit counter to detect strategy effectiveness
		} catch ( EmptyStackException event ) {}
	}

	public void onHitByBullet ( MyTank me, HitByBulletEvent e ) {
		// Get enemy from datacontainer and add hit
		try {
			Enemy enemy = me.getData().getEnemy( e.getName() );
			enemy.addPain( e.getPower() ); // Update hit counter to detect strategy effectiveness
		} catch ( EmptyStackException event ) {}
	}
}
