// CodeClash ladder import
// Source: https://github.com/dankraemer/robocode/blob/HEAD/Juggernaut.java
// Author: dankraemer   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;
import java.awt.Color;
import static robocode.util.Utils.normalRelativeAngleDegrees;

// API help : http://robocode.sourceforge.net/docs/robocode/robocode/Robot.html

/**
 * MyTank - a robot by (your name here)
 */
public class MyTank extends AdvancedRobot
{
	boolean movingForward;

	/**
	 * run: MyTank's default behavior
	 */

	public void run() {

    double randomNum = 0; 
    double posicaoAltura;
    double posicaoLargura;
    double direcao;
    double th;
    
    double turnLimit = 135;
    double arenaAltura  = getBattleFieldHeight();
    double arenaLargura = getBattleFieldWidth();

    th = arenaAltura * 0.25;
    setAhead(100);

	  movingForward= true;

		// Set colors
		setBodyColor(new Color(255, 0, 0));
		setGunColor(new Color(255, 255, 0));
		setRadarColor(new Color(255, 255, 255));
		setBulletColor(new Color(255, 255, 100));
		setScanColor(new Color(255, 200, 200));

		// Robot main loop
		while(true) {

      posicaoAltura = getY();
      posicaoLargura = getX();
      direcao = getHeading();
      
      setTurnGunRight(20);
      
      if ((posicaoAltura > arenaAltura - th)   || (posicaoAltura < th) || 
          (posicaoLargura > arenaLargura - th) || (posicaoLargura < th)){
      
      	if (posicaoAltura > arenaAltura - th){
      		if (direcao < 90) {
      			turnRight(turnLimit);
      		} else if(direcao > 270) {
      			turnLeft(turnLimit);
      		}
      	} else if (posicaoAltura < th){
      		if (direcao > 180) {
      			turnRight(turnLimit);
      		} else if(direcao < 180) {
      			turnLeft(turnLimit);
      		}
      	} else if (posicaoLargura > arenaLargura - th){
      		if (direcao < 90) {
      			turnLeft(turnLimit);
      		} else if(direcao > 90) {
      			turnRight(turnLimit);
      		}
      	} else if (posicaoLargura < th){
      		if (direcao < 270) {
      			turnLeft(turnLimit);
      		} else if(direcao > 270) {
      			turnRight(turnLimit);
      		}
      	}
      	ahead(100);
      } else {
      	randomNum = -180 + Math.random() * 360;
      	setAhead(100);
      	setTurnLeft(randomNum);
      }
      
      execute();
		}
	}

	/**
	 * onScannedRobot: What to do when you see another robot
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
     double gunTurnAmt;

		  gunTurnAmt = normalRelativeAngleDegrees(e.getBearing() + (getHeading() - getRadarHeading()));
		  turnGunRight(gunTurnAmt);

		  fire(3);
  }

	/**
	 * onHitRobot:  Set him as our new target
	 */
	public void onHitRobot(HitRobotEvent e) {
    double gunTurnAmt;
		gunTurnAmt = normalRelativeAngleDegrees(e.getBearing() + (getHeading() - getRadarHeading()));
		turnGunRight(gunTurnAmt);
		fire(3);
		back(100);
	}
	/**
	 * onHitByBullet: What to do when you're hit by a bullet
	 */
	public void onHitByBullet(HitByBulletEvent e) {
		double bulletDirection;
    double direcao;
	  bulletDirection	= e.getHeading();
  	direcao = getHeading();

  	setTurnLeft(bulletDirection - direcao + 90);
	}
	
	/**
	 * onHitWall: What to do when you hit a wall
	 */
	public void onHitWall(HitWallEvent e) {
		out.println("Ouch, I hit a wall bearing " + e.getBearing() + " degrees.");
		back(100);
		turnRight(180);
	}
}
