// CodeClash ladder import
// Source: https://github.com/pranav-prakash/TheCarverBot/blob/HEAD/pt/TheCarver.java
// Author: Pranav Prakash   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

import robocode.Robot;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;


/**
 * Record the advanced state of an enemy bot.
 *
 * @author Pranav Prakash
 * @author Period - 7
 * @author Assignment - AdvancedEnemyBot
 * @author Sources
 * @version 5/12/15
 */
public class AdvancedEnemyBot extends EnemyBot
{

    ///Store x position
    private double x;

    ///Store y position
    private double y;

    ///Store current energy
    private double energy = 0;

    ///Store previous energy
    private double previousEnergy = 0;

    ///Store the last time robot was seen
    private long lastSeenTime = 0;

    ///Stores the previous heading
    // originally set to infinity to signify uninitializated state
    private double prevHeadingRadians = Double.POSITIVE_INFINITY;

    ///Stores the change in heading (in radians) enemy made
    private double headingChange = 0;

    ///Computed turn radius from headingChange and time delta
    private double radius = 0;

    ///Store whether current movement is classified as linear
    private boolean isLinear = true;


    /**
     * Constructor initializes values
     */
    public AdvancedEnemyBot()
    {
        reset();
    }


    /**
     * Update all values
     *
     * @param e        scanned event
     * @param robot    robot object
     * @param lastSeen event time
     */
    public void update( ScannedRobotEvent e, Robot robot, long lastSeen )
    {
        //Update superclass values
        super.update( e );

        //Compute absolute heading and normalize it
        double absBearingDeg = ( robot.getHeading() + e.getBearing() );
        if ( absBearingDeg < 0 )
        {
            absBearingDeg += 360;
        }

        //Update previous and current energy values for enemy
        previousEnergy = energy;
        energy = e.getEnergy();

        //Used if we do not have enough data points yet (we need at least two)
        boolean incompleteScan = false;

        //If previous heading is unitialized (no data so far)
        if ( prevHeadingRadians == Double.POSITIVE_INFINITY )
        {
            //Update necessary values
            prevHeadingRadians = e.getHeadingRadians();
            lastSeenTime = lastSeen;
            incompleteScan = true;
        }
        else if ( lastSeen - getLastSeenTime() > 0 )
        {
            //If we have at least two data points

            //Compute the heading change and normalize it
            headingChange = Utils.normalRelativeAngle(
                            ( e.getHeadingRadians() - prevHeadingRadians ) / (
                                            getLastSeenTime() - lastSeen ) );

            //Compute radius from linear velocity
            // From physics equation: v = r * w (take d/dTheta of s = rTheta)
            // v is linear velocity
            // r is radius
            // w is angular velocity (radians/second)
            radius = e.getVelocity() / headingChange;

            //Update necessary values
            prevHeadingRadians = e.getHeadingRadians();
            lastSeenTime = lastSeen;
        }

        //Update current enemy x and y positions using trigonometry
        //r*Cos(Theta) gives delta x (cos and sin are swapped though
        //as in Robocode 0 degrees is actually 90 degrees
        //and hence you use sin(90-theta)=cos(theta) and vice-versa
        x = robot.getX() + Math.sin( Math.toRadians( absBearingDeg ) )
                        * e.getDistance();
        y = robot.getY() + Math.cos( Math.toRadians( absBearingDeg ) )
                        * e.getDistance();

        //If the movement is linear or we don't have enough data points
        //Use linear prediction instead
        isLinear = Math.abs( headingChange ) <= 0.1 || incompleteScan;
    }


    /**
     * Return whether current movement is linear
     *
     * @return is enemy movement linear
     */
    public boolean isLinear()
    {
        return isLinear;
    }


    /**
     * Get current x
     *
     * @return current x
     */
    public double getX()
    {
        return x;
    }


    /**
     * Get current x
     *
     * @return current y
     */
    public double getY()
    {
        return y;
    }


    /**
     * Gets the last time the robot was seen
     *
     * @return last seen time
     */
    public long getLastSeenTime()
    {
        return lastSeenTime;
    }


    /**
     * Gets future x using linear prediction
     *
     * @param when time
     * @return future x assuming linear path
     */
    public double getFutureXLinear( long when )
    {
        //getVelocity()*when gives hypotenuse distance
        //use deltaX = r*Cos(theta)
        //after taking into account that sin/cos are flipped
        return x + Math.sin( Math.toRadians( getHeading() ) ) * getVelocity()
                        * when;
    }


    /**
     * Gets future y assuming linear path
     *
     * @param when time
     * @return future y assuming linear path
     */
    public double getFutureYLinear( long when )
    {
        //getVelocity()*when gives hypotenuse distance
        //use deltaY = r*Sin(theta)
        //after taking into account that sin/cos are flipped
        return y + Math.cos( Math.toRadians( getHeading() ) ) * getVelocity()
                        * when;
    }


    public double getFutureYCircular( long when )
    {
        // (theta/time) * time gives delta theta, or total heading change
        double totalHeadingChange = when * headingChange;

        //Comes as a result of integrating
        //Integrate[enemyVelocity * Cos[initialHeading + turnRate*n], {n, 0, t}]
        return y + ( Math.sin( Math.toRadians( getHeading() ) ) * radius ) - (
                        Math.sin( Math.toRadians( getHeading() )
                                        + totalHeadingChange ) * radius );
    }


    public double getFutureXCircular( long when )
    {
        // (theta/time) * time gives delta theta, or total heading change
        double totalHeadingChange = when * headingChange;

        //Comes as a result of integrating
        //Integrate[enemyVelocity * Sin[initialHeading + turnRate*n], {n, 0, t}]
        //plus starting position which is x
        return x + ( Math.cos( Math.toRadians( getHeading() ) ) * radius ) - (
                        Math.cos( Math.toRadians( getHeading() )
                                        + totalHeadingChange ) * radius );
    }


    /**
     * Gets current energy level
     *
     * @return Current energy level
     */
    public double getEnergy()
    {
        return energy;
    }


    /**
     * Gets previous energy level
     *
     * @return previous energy level
     */
    public double getPreviousEnergy()
    {
        return previousEnergy;
    }


    /**
     * Reset ALL the things
     */
    public void reset()
    {
        super.reset();
        x = 0;
        y = 0;
        energy = 0;
        previousEnergy = 0;
        lastSeenTime = 0;
        prevHeadingRadians = 0;
        headingChange = 0;
        radius = 0;
    }

}