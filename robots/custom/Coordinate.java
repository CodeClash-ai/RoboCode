// CodeClash ladder import
// Source: https://github.com/gjgomez/RoboCodeSample/blob/HEAD/Mb2.java
// Author: gjgomez   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

/**
 * MyClass - a class by (your name here)
 */
public class Coordinate
{
	public double x;
	public double y;

	public Coordinate()
	{
	}

	public Coordinate(double px, double py)
	{
		this.x = px;
		this.y = py;
	}

	public void set(double px, double py)
	{
		this.x = px;
		this.y = py;
	}
}
