// CodeClash ladder import
// Source: https://github.com/denssle/Robocode/blob/HEAD/MegaBorsten2000.java
// Author: denssle   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import robocode.*;

import java.awt.Color;

/**
 * Borsten - a robot by Dome
 * Ideen: Bullets verfolgen; eigene und feindliche
 * Zirkeln
 */
public class MyTank extends AdvancedRobot {
    //für Radar- und Fahrtrichtungswechsel
    byte dirRadar = 1;
    byte dirDrive = 1;
    int i;

    public void run() {
        setAdjustRadarForGunTurn(true);

        setGunColor(Color.orange);
        setScanColor(Color.lightGray);
        setRadarColor(Color.darkGray);
        setBodyColor(Color.gray);
        setBulletColor(Color.white);

        while (true) {
            i++;
            System.out.println("\nRunde: " + i);
            if (getGunHeat() <= 0)
                System.out.println("Ready to fire!");
            stayAway();
            setTurnRadarLeft(55 * dirRadar);
            setAhead(40 * dirDrive);

            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        dirRadar *= -1;
        setTurnRadarRight(getHeading() - getRadarHeading() + e.getBearing());
        //get bearing of scanned robot -180 to 180
        double bearing = e.getBearing();
        double distance = e.getDistance();

        //get heading of scanned robot 0 -360
        //System.out.println("H: "+heading+" EH: "+getHeading() + " y: "+y);
        //get energy of scanned robot
        //double energy = e.getEnergy();
        String name = e.getName();
        //double velocity = e.getVelocity();
        if (distance < 440) {
            fireControl(bearing, distance, name);
        }
        if (distance < 370) {
            setTurnRight(bearing + 90);
        } else {
            setTurnRight(bearing + 20);
        }
    }

    public void stayAway() {
        double h = getBattleFieldHeight();
        double w = getBattleFieldWidth();
        double x = getX();
        double y = getY();
        //System.out.println("X: "+getX()+" Y: "+getY()+" H: "+getHeading());
        if (y < h / 10) {
            System.out.println("//zu weit unten");
            //setMaxVelocity(0);
        } else if (y > h - h / 10) {
            System.out.println("//zu weit oben");
        } else if (x < w / 10) {
            System.out.println("//zu weit links");
        } else if (x > h - h / 10) {
            System.out.println("//zu weit rechts");
        }
    }

    public void fireControl(double bearing, double distance, String name) {
        double heading = getHeading();
        //Returns direction the gun is facing, in degrees. The value will be between 0 and 360
        double gunheading = getGunHeading();

        double x = heading + bearing + (gunheading * -1);

        if (x >= 181) {
            //System.out.println("Korrigiere von X = "+x);
            x = x - 360;
        } else if (x <= -181) {
            //System.out.println("Korrigiere von X = "+x);
            x = x + 360;
        }

        if (getGunHeat() <= 0 && getEnergy() > 5) {
            //System.out.println("Distance to the enemy: "+distance + "  X: "+x);
            turnGunRight(x);
            openFire(distance, name);
        }
    }

    public void openFire(double distance, String name) {
        System.out.println("Open the fire on " + name + "!");
        short max = 290;
        byte min = 50;

        if (distance <= min) {
            fire(Rules.MAX_BULLET_POWER);
        }
        if ((distance > min) && (distance <= max)) {
            fire(Rules.MAX_BULLET_POWER / 2);
        }
        fire(Rules.MAX_BULLET_POWER / 3);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        double bearing = e.getBearing();
        Bullet bulltet = e.getBullet();
        double heading = e.getHeading();

        fuckYou(e.getName());
    }

    public void onHitRobot(HitRobotEvent e) {
        double energy = e.getEnergy();
        boolean myfault = e.isMyFault();

        if (myfault == true && energy > getEnergy()) {
            setAhead(102);
        } else {
            setBack(99);
        }

        fireControl(e.getBearing(), 22.2, e.getName());

        fuckYou(e.getName());
    }

    public void onHitWall(HitWallEvent e) {
        dirDrive *= -1;
    }

    public void onBulletMissed(BulletMissedEvent e) {
        System.out.println("Damn!");
    }

    public void onRobotDeath(RobotDeathEvent e) {
        // other robot dies...
        fuckYou(e.getName());
    }

    public void onWinEvent() {
        //party hard!
    }

    public void onRoundEnded(RoundEndedEvent event) {
        System.out.println("Round " + i + " was the last!");
    }

    public void fuckYou(String name) {
        String[] fuckyou = {"Fuck you ",
                "I hate you ",
                "You´re a piece of shit, ",
                "You are worthless trash ",
                "Candy Ass ",
                "Hit the road, ",
                "Eat my shorts, ",
                "You've got the brains of a doughnut, ",
                "You´re a randy old bugger, "
        };
        int rand = (int) (Math.random() * fuckyou.length);
        System.out.println(fuckyou[rand] + name + "!");
    }
}
