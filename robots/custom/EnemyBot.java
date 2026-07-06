// CodeClash ladder import
// Source: https://github.com/pranav-prakash/TheCarverBot/blob/HEAD/pt/TheCarver.java
// Author: Pranav Prakash   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

import robocode.ScannedRobotEvent;


/**
 * Record the state of an enemy bot.
 *
 * @author Pranav Prakash
 * @author Period - 7
 * @author Assignment - EnemyBot
 * @author Sources
 * @version 5/8/15
 */
public class EnemyBot
{
    ///Stores bearing
    private double bearing;

    ///Stores distance
    private double distance;

    ///Stores energy
    private double energy;

    ///Stores heading
    private double heading;

    ///Stores velocity
    private double velocity;

    ///Stores name
    private String name;


    /**
     * Constructor for enemy bot
     */
    public EnemyBot()
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
     * Gets distance
     *
     * @return distance
     */
    public double getDistance()
    {
        return distance;
    }


    /**
     * Gets energy
     *
     * @return energy
     */
    public double getEnergy()
    {
        return energy;
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
    public void update( ScannedRobotEvent srEvt )
    {
        bearing = srEvt.getBearing();
        distance = srEvt.getDistance();
        energy = srEvt.getEnergy();
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
        distance = 0;
        energy = 0;
        heading = 0;
        velocity = 0.0;
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