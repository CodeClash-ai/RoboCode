// CodeClash ladder import
// Source: https://github.com/johan-adriaans/BerendBotje/blob/HEAD/src/hackersNL/BerendBotje.java
// Author: Johan Adriaans   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

import java.util.EmptyStackException;
import robocode.ScannedRobotEvent;

public class ScanStrategy extends Strategy
{
	public int ticks = 0;

	public ScanStrategy()
	{
		setType( TYPE_SCAN );
	}

	/**
	 * Do a full 360 scan, store all bots in the DataContainer, select new target and set new strategy
	 */
	public void onTick( MyTank me )
	{
		if ( ticks == 0 ) {
		  me.setTurnRadarLeft( 360 );
		}

		if ( ticks > 0 && me.getRadarTurnRemaining() == 0 ) {
			try {
				me.getData().setTarget( me.getData().getClosestEnemy() );
				me.addStrategy( new AggressiveStrategy() );
			} catch ( EmptyStackException e ) {
				System.out.println( "No more enemies.." );
			}
		}

		me.scan(); // Do something

		ticks++;
		super.onTick( me );
	}

	@Override
	public void onScannedRobot( MyTank me, ScannedRobotEvent e )
	{
		me.getData().addEnemy( new Enemy( me, e ) );
		super.onScannedRobot(me, e);
	}
}
