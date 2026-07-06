// CodeClash ladder import
// Source: https://github.com/pranav-prakash/TheCarverBot/blob/HEAD/pt/TheCarver.java
// Author: Pranav Prakash   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

import robocode.*;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;


/**
 * MyTank -- A modular bot adhering to the RoboPart Interface.
 * <p/>
 * Multi-modal radar and movement
 * <p/>
 * 1v1 Mode:
 * Perfect lock radar with 1pt beam diameter
 * Switches between two different movement patterns based on win rate
 * Randomized movement designed to counter pattern matching
 * Stop/go movement designed to counter linear/radial prediction
 * <p/>
 * Melee Mode:
 * Corner movement
 * Sweeping radar that chooses target based on various factors
 * Switches beam diameter at regular intervals
 * Switches into 1v1 mode when necessary
 *
 * @author Pranav Prakash
 * @author Period - 7
 * @author Assignment - PartsBot
 * @author Sources - LemonDrop, Acero, Lib, Robowiki
 * @version 5/14/15
 */
public class MyTank extends AdvancedRobot
{
    ///Hold information about currently targetted enemy (heading, speed, etc.)
    private AdvancedEnemyBot enemy = new AdvancedEnemyBot();

    ///Hold information about the last bullet that impacted me
    private BulletLocation bul = new BulletLocation();

    ///Array of hot-swappable modular robot parts
    private RobotPart[] parts = new RobotPart[3]; // make three parts

    ///Indices of those parts

    ///Radar is index 0
    private final static int RADAR = 0;

    ///Gun is index 1
    private final static int GUN = 1;

    ///Tank is index 2
    private final static int TANK = 2;

    ///Hold the current beam diameter
    double scanWidth = -1;

    ///Threshold to switch into 1v1 mode from melee mode
    private final int ONEvONE_THRESHOLD = 1;

    ///Whether we are in melee mode right now
    boolean isMeleeMode = false;

    ///Check whether our current movement schema is the first one (Oscillating)
    boolean isMovementOne = true;

    /// Hashmap mapping enemy to prior win statistics
    static HashMap<String, MovementHistory> moveHistoryMap = new HashMap<String, MovementHistory>();


    /**
     * computes the absolute bearing between two points
     *
     * @param x1 point1x
     * @param y1 point1y
     * @param x2 point2x
     * @param y2 point2y
     * @return absolute bearing
     */
    public double absoluteBearing( double x1, double y1, double x2, double y2 )
    {
        double xo = x2 - x1;
        double yo = y2 - y1;
        double hyp = Point2D.distance( x1, y1, x2, y2 );
        double arcSin = Math.toDegrees( Math.asin( xo / hyp ) );
        double bearing = 0;

        if ( xo > 0 && yo > 0 )
        { // both pos: lower-Left
            bearing = arcSin;
        }
        else if ( xo < 0 && yo > 0 )
        { // x neg, y pos: lower-right
            bearing = 360 + arcSin; // arcsin is negative here, actually 360 -
            // ang
        }
        else if ( xo > 0 && yo < 0 )
        { // x pos, y neg: upper-left
            bearing = 180 - arcSin;
        }
        else if ( xo < 0 && yo < 0 )
        { // both neg: upper-right
            bearing = 180 - arcSin; // arcsin is negative here, actually 180 +
            // ang
        }

        return bearing;
    }


    /**
     * Normalizes normalizes a bearing to between +180 and -180
     *
     * @param angle angle to normalize
     * @return normalized
     */
    public double normalizeBearing( double angle )
    {
        //Subtracts multiples of 360 degrees until angle is between [-180, 180]
        while ( angle > 180 )
        {
            angle -= 360;
        }
        while ( angle < -180 )
        {
            angle += 360;
        }
        return angle;
    }


    /**
     * Runs the bot
     */
    public void run()
    {
        //Some sick colors to make radar/bullets appear invisible
        Color bodyColor = new Color( 61, 66, 96 );
        Color gunColor = new Color( 61, 66, 96 );
        Color radarColor = new Color( 61, 66, 96 );
        Color bulletColor = new Color( 61, 66, 96 );
        Color scanArcColor = new Color( 61, 66, 96 );

        setColors( bodyColor, gunColor, radarColor, bulletColor, scanArcColor );

        // If melee mode
        if ( getOthers() > ONEvONE_THRESHOLD )
        {
            System.out.println( "Melee Mode" );
            isMeleeMode = true;
            parts[RADAR] = new RadarMelee();
            parts[GUN] = new Gun();
            parts[TANK] = new TankMelee();
        }
        else // If one on one
        {
            System.out.println( "1v1 Mode" );
            isMeleeMode = false;
            parts[RADAR] = new Radar1v1();
            parts[GUN] = new Gun();
            parts[TANK] = new Tank1v1();
        }

        // initialize each part
        for ( RobotPart part : parts )
        {
            // behold, the magic of polymorphism
            part.init();
        }

        // iterate through each part, moving them as we go
        int i = 0;
        while ( true )
        {
            // polymorphism galore!
            parts[i].move();
            if ( i == 0 )
            {
                execute();
            }
            i = ( i + 1 ) % parts.length;
        }
    }


    /**
     * On robot scan
     *
     * @param e scanevent
     */
    public void onScannedRobot( ScannedRobotEvent e )
    {
        //Downcast the robot part to a radar (which is itself an interface)
        Radar radar = (Radar)parts[RADAR];

        //If you are currently tracking this bot
        // or (for Melee mode) if it is the bot that shot at you
        if ( radar.shouldTrack( e ) || e.getName().equals( bul.getName() ) )
        {
            //Reset volatile bullet info
            // (no longer needed as you have found your enemy)
            if ( e.getName().equals( bul.getName() ) )
                bul.reset();

            //Update enemy information
            enemy.update( e, this, getTime() );
        }
    }


    /**
     * Action to take when bullet hits me
     *
     * @param event HitByBulletEvent
     */
    public void onHitByBullet( HitByBulletEvent event )
    {
        //Increase scan width so that (in Melee mode) robots are easier to find
        if ( getEnergy() > 30 )
        {
            if ( scanWidth != -1 )
                scanWidth += 5;
            //Update bullet information (velocity, bearing, etc.)
            bul.update( event );
        }

        //Get out of the way
        setAhead( 100 );

        //Debugging information
        //System.out.println( "Ouch. Speed:" + bul.getVelocity() );

    }


    /**
     * Action to take when you hit another robot
     *
     * @param e HitRobotEvent
     */
    public void onHitRobot( HitRobotEvent e )
    {
        //Fire with maximum power since we are head to head
        double turnGunAmt = normalizeBearing(
                        e.getBearing() + getHeading() - getGunHeading() );
        turnGunRight( turnGunAmt );
        fire( 3 );
    }


    /**
     * Action to take if we hit a wall
     *
     * @param event HitWallEvent
     */
    public void onHitWall( HitWallEvent event )
    {
        //Should never happen
        System.out.println( "Ouch. I hit a wall" );
    }


    /**
     * Behavior to take when robot dies
     * <p/>
     * <p/>
     * Source: Robocode LemonDropBot
     * github.com/axelson/ICS606-Robocode/
     *
     * @param event DeathEvent
     */
    public void onDeath( DeathEvent event )
    {
        // If movement is unsuccessful, switch to a new movement.

        if ( !isMeleeMode ) //Only switch movement if we lose in 1v1 mode
        {
            //Get the previous win rates against current enemy
            MovementHistory hist = moveHistoryMap.get( enemy.getName() );

            //Debugging info
            System.out.println( ( hist.chosenMovement < 105 ) ?
                            "Oscillate" :
                            "StopGo" );

            //If we lose and haven't won three with movement 1 yet,
            // gradually switch to movement 2
            if ( hist.winsWithMovement1 < 3 )
            {
                hist.chosenMovement += ( 35 * hist.bestMoveNotFound );
                // Increase movement number if we haven't found best movement
                // yet (when bestMoveNotFound = 1)
            }

            //Update win frequencies
            if ( hist.chosenMovement < 110 )    // If we are using movement 1
            {
                hist.move1Effectiveness =
                                hist.move1Effectiveness - enemy.getEnergy();
                // record how much energy the enemy had left
            }
            else // and if we are using movement 2
            {
                hist.move2Effectiveness =
                                hist.move2Effectiveness - enemy.getEnergy();
                // record how much energy the enemy had left
            }
        }
    }


    /**
     * Behavior to take when robot wins
     * <p/>
     * Source: Robocode LemonDropBot
     * github.com/axelson/ICS606-Robocode/
     *
     * @param event DeathEvent
     */
    public void onWin( WinEvent event )
    {
        // If movement is successful, increment current movement effectiveness

        if ( !isMeleeMode ) //Only record data  if we win in 1v1 mode
        {
            //Get the previous win rates against current enemy
            MovementHistory hist = moveHistoryMap.get( enemy.getName() );

            //Debugging info
            System.out.println( ( hist.chosenMovement < 105 ) ?
                            "Oscillate" :
                            "StopGo" );

            if ( hist.chosenMovement < 1 )  // and are using movement 1
            {
                // record how much energy we had left
                hist.move1Effectiveness = hist.move1Effectiveness + getEnergy();

                // add a win to the movement 1 counter
                hist.winsWithMovement1 += 1;
            }
            else // and if we are using movement 2
            {
                // record how much energy we had left
                hist.move2Effectiveness = hist.move2Effectiveness + getEnergy();
            }
        }
    }


    /**
     * When an enemy robot dies
     *
     * @param e RobotDeathEvent
     */
    public void onRobotDeath( RobotDeathEvent e )
    {
        //Downcast to a Radar object
        Radar radar = (Radar)parts[RADAR];

        // If it is now 1v1, switch out parts
        if ( getOthers() != 0 && getOthers() <= ONEvONE_THRESHOLD )
        {
            System.out.println( "Switched to 1v1 mode" );
            isMeleeMode = false;
            parts[RADAR] = new Radar1v1();
            parts[GUN] = new Gun();
            parts[TANK] = new Tank1v1();

            // initialize each part
            for ( RobotPart part : parts )
            {
                // behold, the magic of polymorphism
                part.init();
            }

        }

        //If the robot that died was the one that hit us, reset
        if ( e.getName().equals( bul.getName() ) )
        {
            bul.reset();
        }

        //If the robot that died was our current enemy, reset
        if ( radar.wasTracking( e ) )
        {
            enemy.reset();
        }
    }


    /**
     * Melee Radar
     *
     * @author Pranav Prakash
     * @author Period: 7
     * @author Assignment: Robo05PartsBot
     * @author Source: RoboWiki Perfect Width Lock Radar
     *         http://robowiki.net/wiki/One_on_One_Radar
     * @version May 14, 2015
     */
    public class RadarMelee implements Radar
    {

        /**
         * Initialize variables and call needed methods
         */
        public void init()
        {
            //Initialize variables
            setAdjustRadarForGunTurn( true );
            setAdjustRadarForRobotTurn( true );
            setTurnRadarRight( 3600 );
            scanWidth = 100;
        }


        /**
         * Movement for radar
         */
        public void move()
        {
            //If we have not found our enemy
            // (either through stored EnemyBot or through last bullet info)
            if ( !bul.none() && !enemy.getName().equals( bul.getName() ) )
            {
                // Absolute angle towards target
                double enemyHeading = getHeading() + bul.getBearing();

                // Subtract current radar heading to get the turn required to
                // face the enemy, be sure it is normalized
                double radarTurn = normalizeBearing(
                                enemyHeading - getRadarHeading() );

                //Turn radar
                setTurnRadarRight( radarTurn );
            }
            else if ( enemy.none() )
            {
                // If no enemy currently found, keep spinning
                setTurnRadarRight( 3600 );
                scanWidth = 100;
            }
            else  // If no enemy currently found, keep spinning
            {
                //If our gun heat is too high, we can't shoot
                // anyway so keep searching for best enemy
                if ( getGunHeat() > 2 )
                {
                    //Keep increasing scanwidth to find enemies faster
                    scanWidth = Math.min( scanWidth + 1, 10 );
                }
                else if ( scanWidth > 4 )
                {
                    //Else if we have locked onto a bot,
                    // gradually narrow beam width
                    scanWidth--;
                }

                // Absolute angle towards target
                double enemyHeading = getHeading() + enemy.getBearing();

                // Subtract current radar heading to get the turn required to
                // face the enemy, be sure it is normalized
                double radarTurn = normalizeBearing(
                                enemyHeading - getRadarHeading() );

                // Distance we want to scan from middle of enemy to either side
                // scanWidth is how many units from the center of the enemy
                // robot it scans.
                double extraScan = Math.min( scanWidth + Math.atan(
                                                scanWidth / enemy.getDistance()
                                                                * 180 / 3.14 ),
                                50 );

                // Adjust the radar turn so it goes that much further in the
                // direction it is going to turn
                // Basically if we were going to turn it left, turn it even more
                // left, if right, turn more right.
                // This allows us to overshoot our enemy so that we get a good
                // sweep that will not slip.
                radarTurn += radarTurn >= 0 ? extraScan : -extraScan;

                //Turn radar
                setTurnRadarRight( radarTurn );

            }
        }


        /**
         * Should track
         *
         * @param e robotevent
         * @return is tracked
         */
        public boolean shouldTrack( ScannedRobotEvent e )
        {
            // track if we have no enemy, the one we found is significantly
            // closer, or we scanned the one we've been tracking.
            return ( ( enemy.none() || e.getDistance() < enemy.getDistance()
                            || ( e.getEnergy() < 40
                            && e.getDistance() < enemy.getDistance() * 2 )
                            || e.getName().equals( enemy.getName() ) ) );
        }


        /**
         * Used to check whether robot that died
         * was the one that you are tracking
         *
         * @param e event
         * @return was previously tracking
         */
        public boolean wasTracking( RobotDeathEvent e )
        {
            return e.getName().equals( enemy.getName() );
        }
    }


    /**
     * 1v1 Radar
     *
     * @author Pranav Prakash
     * @author Period: 7
     * @author Assignment: Robo05PartsBot
     * @author Source: RoboWiki Perfect Width Lock Radar
     *         http://robowiki.net/wiki/One_on_One_Radar
     * @version May 14, 2015
     */
    public class Radar1v1 implements Radar
    {
        /**
         * Initializes by calling needed methods
         */
        public void init()
        {
            setAdjustRadarForGunTurn( true );
            setAdjustRadarForRobotTurn( true );
            setTurnRadarRight( 3600 );
        }


        /**
         * Movement for 1v1 radar
         */
        public void move()
        {
            ///Stores the scan width
            double scanWidth = 1.0;

            if ( enemy.none() )
            {
                //If no enemy found, keep spinning
                setTurnRadarRight( 3600 );
            }
            else
            {
                // Absolute angle towards target
                double enemyHeading = getHeading() + enemy.getBearing();

                // Subtract current radar heading to get the turn required to
                // face the enemy, be sure it is normalized
                double radarTurn = normalizeBearing(
                                enemyHeading - getRadarHeading() );

                // Distance we want to scan from middle of enemy to either side
                // scanWidth is how many units from the center of the enemy
                // robot it scans.
                double extraScan = Math.min( Math.atan(
                                                scanWidth / enemy.getDistance()
                                                                * 180 / 3.14 ),
                                45 );

                // Adjust the radar turn so it goes that much further in the
                // direction it is going to turn
                // Basically if we were going to turn it left, turn it even more
                // left, if right, turn more right.
                // This allows us to overshoot our enemy so that we get a good
                // sweep that will not slip.
                radarTurn += radarTurn >= 0 ? extraScan : -extraScan;

                //Turn radar
                setTurnRadarRight( radarTurn );

            }
        }


        /**
         * Should track
         *
         * @param e ScannedRobotEvent
         * @return is tracked
         */
        public boolean shouldTrack( ScannedRobotEvent e )
        {
            // track if we have no enemy, the one we found is significantly
            // closer, or we scanned the one we've been tracking.
            return ( enemy.none() || e.getDistance() < enemy.getDistance() - 70
                            || e.getName().equals( enemy.getName() ) );
        }


        /**
         * Check whether previously tracking
         *
         * @param e RobotDeathEvent
         * @return was previously tracking
         */
        public boolean wasTracking( RobotDeathEvent e )
        {
            // checks if robot that died is robot we targeted
            return e.getName().equals( enemy.getName() );
        }
    }


    /**
     * Robot gun
     *
     * @author Pranav Prakash
     * @author Period: 7
     * @author Assignment: Robo05PartsBot
     * @author Sources: PartsBot
     * @version May 14, 2015
     */
    public class Gun implements RobotPart
    {
        /**
         * Initialize
         */
        public void init()
        {
            // divorce gun movement from tank movement
            setAdjustGunForRobotTurn( true );
        }


        /**
         * Handle gun movement and shooting
         * <p/>
         * Source: IBM Robocode Secrets
         * ibm.com/developerworks/library/j-circular/
         */
        public void move()
        {
            // don't shoot if I've got no enemy
            if ( enemy.none() )
            {
                // exit method
                return;
            }

            // calculate fire-power based on distance
            // 3 is maximum firepower for robot
            // 1.2 is a good average low-power constant value
            double firePower = ( getEnergy() > 20 ) ?
                            Math.min( 500 / enemy.getDistance(), 3 ) :
                            1.2;

            //Use linear prediction if we are oscillating
            // or enemy movement is locally linear
            if ( enemy.isLinear() || isMovementOne )
            {
                // calculate speed of bullet
                double bulletSpeed = 20 - firePower * 3;
                // distance = rate * time, solved for time
                long time = (long)( enemy.getDistance() / bulletSpeed );

                // calculate gun turn to predicted x,y location
                double futureX = enemy.getFutureXLinear( time );
                double futureY = enemy.getFutureYLinear( time );

                double absDeg = absoluteBearing( getX(),
                                getY(),
                                futureX,
                                futureY );

                // turn the gun to the predicted x,y location
                setTurnGunRight( normalizeBearing( absDeg - getGunHeading() ) );
            }
            else //If we are using stop go then circular is almost always better
            {
                // calculate speed of bullet
                double bulletSpeed = 20 - firePower * 3;

                // distance = rate * time, solved for time
                long time = (long)( enemy.getDistance() / bulletSpeed );

                // calculate gun turn to predicted x,y location
                double futureX = enemy.getX();
                double futureY = enemy.getY();

                //iterate to refine circular approximation
                for ( int i = 0; i < 10; i++ )
                {
                    //Compute new time as distance / rate
                    time = (long)( Point2D.distance( getX(),
                                    getY(),
                                    futureX,
                                    futureY ) / bulletSpeed );

                    //Get new, better futureX and futureY coordinates
                    futureX = enemy.getFutureXCircular( time );
                    futureY = enemy.getFutureYCircular( time );
                }

                //Find the absolute heading of future enemy
                double absDeg = absoluteBearing( getX(),
                                getY(),
                                futureX,
                                futureY );

                // turn the gun to the predicted angle
                setTurnGunRight( normalizeBearing( absDeg - getGunHeading() ) );
            }

            // if the gun is cool and we're pointed in the right direction,
            // shoot!
            if ( getGunHeat() == 0 && Math.abs( getGunTurnRemaining() ) < 10 )
            {
                setFire( firePower );
            }
        }
    }


    /**
     * Tank movement for Melee mode
     *
     * @author Pranav Prakash
     * @author Period: 7
     * @author Assignment: Robo05PartsBot
     * @author Sources: Robocode Lib Nanobot
     *         http://robowiki.net/wiki/CornerMovement
     * @version May 14, 2015
     */
    public class TankMelee implements Tank
    {
        //Stores the length of our movement pattern
        private final int PATTERN_LENGTH = 8;

        //These are the coordinates of
        // our corner movement (if it was in the lower-left corner).

        //Can be flipped as necessary for other corners
        //Change directions (as in forward or reverse) for each point.
        private final int[] xcoords = { 30, 30, 90, 30, 150, 150, 30, 210 };

        private final int[] ycoords = { 30, 210, 30, 150, 150, 30, 90, 30 };

        //Store flippable direction
        private double direction = Math.PI / 2;

        //Distance to move with each stride
        private double moveDist = 64.4;

        //Store current index in movement pattern
        int index = 0;


        /**
         * Initialize
         */
        public void init()
        {
            //Nothing to initialize here
        }


        /**
         * Move the tank as per the pattern
         */
        public void move()
        {
            //Find and flip y coordinate based on closest corner
            double goalY = ( getY() > getBattleFieldHeight() / 2 ) ?
                            getBattleFieldHeight() - ycoords[index] :
                            ycoords[index];

            //Find and flip x coordinate based on closest corner
            double goalX = ( getX() > getBattleFieldWidth() / 2 ) ?
                            getBattleFieldWidth() - xcoords[index] :
                            xcoords[index];

            if ( Point2D.distance( goalX, goalY, getX(), getY() ) < 10 )
            {
                //If you have pretty much arrived
                // at your destination move on to next point
                direction = -direction;
                moveDist = -moveDist;
                index = ( index + 1 ) % PATTERN_LENGTH;
            }

            setAhead( moveDist );

            setAhead( getTurnRemainingRadians() == 0 ? moveDist : 0 );

            //Turn, accounting for direction flip by adding 180 degrees
            setTurnRightRadians( Utils.normalRelativeAngle(
                            Math.atan2( goalX - getX(), goalY - getY() )
                                            + Math.PI / 2 - direction
                                            - getHeadingRadians() ) );
        }
    }


    /**
     * Tank movement for Melee mode
     *
     * @author Pranav Prakash
     * @author Period: 7
     * @author Assignment: Robo05PartsBot
     * @author Source 1: Robocode Acero Nanobot
     *         http://robowiki.net/wiki/Acero
     * @author Source 2: Robocode LemonDropBot
     *         github.com/axelson/ICS606-Robocode/
     * @version May 14, 2015
     */
    public class Tank1v1 implements Tank
    {

        //Used to store current direction (forwards or backwards)
        double direction = 1;

        //Used in stop-go movement to store current state
        int startStop = 0;

        //Used in stop-go movement to flip forwards-backwards on wall hit
        boolean flip = false;


        /**
         * Initialize
         */
        public void init()
        {
            chooseBestMovement();
        }


        /**
         * Choose best movement based on prior statistics
         * <p/>
         * Source: Robocode LemonDropBot
         * github.com/axelson/ICS606-Robocode/
         */
        public void chooseBestMovement()
        {
            // Determining Most Effective Movement

            //If we have no information for current enemy, create one
            if ( moveHistoryMap.get( enemy.getName() ) == null )
            {
                moveHistoryMap.put( enemy.getName(), new MovementHistory() );
            }

            //Get movement information
            MovementHistory hist = moveHistoryMap.get( enemy.getName() );

            // if we have died 3 times with current movement,
            // time to consider switching
            if ( hist.chosenMovement >= 210 )
            {
                // You have found tentative best movement
                hist.bestMoveNotFound = 0;

                if ( hist.move2Effectiveness > hist.move1Effectiveness )
                {   //if movement 2 performed the best
                    hist.chosenMovement = 150; // use movement 2
                }
                else
                {
                    // if movement 1 performed the best
                    hist.chosenMovement = 0; // use movement 1
                }
            }
        }


        /**
         * Move randomly in order to counter pattern matching
         * <p/>
         * Source: Robocode Acero Nanobot
         * http://robowiki.net/wiki/Acero
         */
        public void oscillate()
        {

            //Turn perpendicular to enemy, maintaining distance at 600
            double goalDirection = ( Math.toRadians( enemy.getBearing() )
                            + getHeadingRadians() ) -
                            ( Math.PI / 2 + ( ( enemy.getDistance() ) >= 600 ?
                                            0 :
                                            0.4 ) ) * direction;

            //Wall smoothing code

            //If you are going to intersect wall in current path
            while ( !new Rectangle2D.Double( 19.0,
                            19.0,
                            getBattleFieldWidth() - 38,
                            getBattleFieldHeight() - 38 ).contains(
                            getX() + Math.sin( goalDirection ) * 120,
                            getY() + Math.cos( goalDirection ) * 120 ) )
            {
                //Turn a little bit towards the enemy
                goalDirection = goalDirection + direction * .1;

                //But sometimes turn the other direction
                // to not be as predictable
                if ( Math.random() < .01 )
                    direction = -direction;
            }

            //Normalize direction
            goalDirection = Utils.normalRelativeAngle(
                            goalDirection - getHeadingRadians() );

            //Use tan function to get a periodic oscillator
            // that is more irregular than sin or cos
            setTurnRightRadians( Math.tan( goalDirection ) );

            //If angle to enemy becomes too steep move the other direction
            setAhead( 100 * ( Math.abs( goalDirection ) > Math.PI / 2 ?
                            -1 :
                            1 ) );

            //Random direction swapping
            if ( Math.random() < .04 )
                direction = -direction;
        }


        /**
         * Move start/stop to counter linear+circular targeting
         * <p/>
         * Source: Robocode Acero Nanobot
         * http://robowiki.net/wiki/Acero
         */
        public void stopAndGo()
        {

            // Stop & Go
            // Second type of movement

            // Square off (perpendicular) against enemy
            // flipping if necessary (to back out from walls)
            double goalDirection = ( Math.toRadians( enemy.getBearing() )
                            + getHeadingRadians() ) - ( flip ?
                            -Math.PI / 2 :
                            Math.PI / 2 );

            // Wall smoothing code

            //If you are going to intersect wall in current path
            while ( !new Rectangle2D.Double( 20.0,
                            20.0,
                            getBattleFieldWidth() - 50,
                            getBattleFieldHeight() - 50 ).contains(
                            getX() + Math.sin( goalDirection ) * 100,
                            getY() + Math.cos( goalDirection ) * 100 ) )
            {
                //Turn a little bit towards the enemy
                goalDirection = goalDirection - 0.1;

                //Indicate that we need to change our goal direction to back out
                flip = !flip;
            }

            //Normalize direction
            goalDirection = Utils.normalRelativeAngle(
                            goalDirection - getHeadingRadians() );

            //Use tan periodicity to make it not so easy to predict
            setTurnRightRadians( Math.tan( goalDirection ) );

            //Does three things:

            //Choose random dist (min 50) in your start-stop movements
            //Avoid becoming too parallel to enemy (angle to steep)
            //Only move if startStop = 1
            setAhead( ( 50 + Math.random() * 50 ) * (
                            Math.abs( goalDirection ) > Math.PI / 2 ? -1 : 1 )
                            * startStop );

            //When we detect an energy drop, assume bullet fired
            if ( enemy.getPreviousEnergy() > ( enemy.getEnergy() ) )
            { // if the enemy fires
                if ( startStop == 0 )
                { // and we aren't moving,
                    startStop = 1; // move.
                }
                else
                { // but if we are moving,
                    startStop = 0; // stop.
                }
            }

        }


        /**
         * Dispatches to appropriate move method
         * based on currently chosen movement
         * <p/>
         * Source: Robocode LemonDropBot
         * github.com/axelson/ICS606-Robocode/
         */
        public void move()
        {
            chooseBestMovement();
            MovementHistory hist = moveHistoryMap.get( enemy.getName() );
            if ( hist.chosenMovement < 105 )
            {
                oscillate();
                isMovementOne = true;
            }
            else if ( hist.chosenMovement >= 105 && hist.chosenMovement < 210 )
            {
                stopAndGo();
                isMovementOne = false;
            }
        }
    }
}
