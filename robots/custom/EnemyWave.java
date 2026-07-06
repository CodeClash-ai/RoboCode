// CodeClash ladder import
// Source: https://github.com/winstliu/robocode/blob/HEAD/exam2016/BobTheBuilder.java
// Author: winstliu   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

import java.awt.geom.*;

public class EnemyWave
{
	private Point2D.Double fireLocation;
	private long fireTime;
	private double bulletVelocity;
	private double directAngle;
	private double distanceTraveled;
	private int direction;

	public EnemyWave()
	{
		// Noop
	}

	public Point2D.Double getFireLocation()
	{
		return fireLocation;
	}

	public void setFireLocation(Point2D.Double location)
	{
		fireLocation = location;
	}

	public long getFireTime()
	{
		return fireTime;
	}

	public void setFireTime(long time)
	{
		fireTime = time;
	}

	public double getBulletVelocity()
	{
		return bulletVelocity;
	}

	public void setBulletVelocity(double velocity)
	{
		bulletVelocity = velocity;
	}

	public double getDirectAngle()
	{
		return directAngle;
	}

	public void setDirectAngle(double angle)
	{
		directAngle = angle;
	}

	public double getDistanceTraveled()
	{
		return distanceTraveled;
	}

	public void setDistanceTraveled(double distance)
	{
		distanceTraveled = distance;
	}

	public int getDirection()
	{
		return direction;
	}

	public void setDirection(int newDirection)
	{
		direction = newDirection;
	}
}
