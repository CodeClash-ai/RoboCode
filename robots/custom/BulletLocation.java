// CodeClash ladder import
// Source: https://github.com/pranav-prakash/TheCarverBot/blob/HEAD/pt/TheCarver.java
// Author: Pranav Prakash   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

import robocode.HitByBulletEvent;


/**
 * Record the state of impacted bullet (that hit you)
 *
 * @author Pranav Prakash
 * @author Period - 7
 * @author Assignment - EnemyBot
 * @author Sources
 * @version 5/8/15
 */
public class BulletLocation
{
    ///Stores bullet bearing
    private double bearing;

    ///Stores power of received bullet
    private double power;

    ///Stores heading
    private double heading;

    ///Stores velocity
    private double velocity;

    ///Stores name
    private String name;


    /**
     * Constructor for BulletLocation
     */
    public BulletLocation()
    {
        reset();
    }


    /**
     * Gets bearing
     *
     * @return bearing
     */
    public double getBearing()
    {
        return bearing;
    }


    /**
     * Gets energy
     *
     * @return energy
     */
    public double getPower()
    {
        return power;
    }


    /**
     * Gets heading
     *
     * @return heading
     */
    public double getHeading()
    {
        return heading;
    }


    /**
     * Gets velocity
     *
     * @return velocity
     */
    public double getVelocity()
    {
        return velocity;
    }


    /**
     * Gets name
     *
     * @return name
     */
    public String getName()
    {
        return name;
    }


    /**
     * Updates values
     *
     * @param srEvt event
     */
    public void update( HitByBulletEvent srEvt )
    {
        bearing = srEvt.getBearing();
        power = srEvt.getPower();
        heading = srEvt.getHeading();
        velocity = srEvt.getVelocity();
        name = srEvt.getName();
    }


    /**
     * Resets all
     */
    public void reset()
    {
        name = "";
        bearing = 0;
        power = 0;
        heading = 0;
        //Not reseting velocity on purpose
    }


    /**
     * Checks if cleared
     *
     * @return Whether cleared
     */
    public boolean none()
    {
        return name.length() == 0;
    }
}