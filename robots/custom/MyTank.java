// CodeClash ladder import
// Source: https://github.com/kcanida/robocode-kkc-pikachu/blob/HEAD/src/main/java/kkc/Pikachu.java
// Author: kcanida   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;

import java.awt.Color;
import robocode.AdvancedRobot;
import robocode.HitRobotEvent;
import robocode.ScannedRobotEvent;
import static robocode.util.Utils.normalRelativeAngleDegrees;

/**
 * MyTank (originally Pikachu) is a robot in robocode that tracks enemy robot's energy levels.
 * If the enemy has a drop in energy we assume the enemy fired a bullet and
 * our robot changes directions to avoid the bullet.  Then our robot scans
 * its radar to lock the gun on the enemy and fire bullet with power varying
 * according to distance of enemy.
 *
 * @author Kellie Canida
 *
 */
public class MyTank extends AdvancedRobot {

  /**Initial energy of enemy at start of battle.*/
  private double energyOfEnemy = 100;
  /**Direction our robot will move.*/
  private int directionToMove = 1;
  /**Initialized to 1 to create oscillating effect.*/
  private int scanDirection = 1;
  /**Distance of enemy from our robot.*/
  private double distance = 0;

  /**
   * Main run function.
   */
  @Override
  public final void run() {
    //Set the color of our robot
    setColors(new Color(229, 218, 42), new Color(231, 74, 69),
        Color.black, new Color(255, 255, 1), Color.pink);

    //Do an initial radar scan of entire field to pick up our enemy robot.
    setTurnRadarRight(360 * scanDirection);
  }

  /**
   * OnScannedRobot, this robot will determine if a bullet is
   * fired by the enemy. If a bullet is fired, it will change
   * direction then use its radar to target the enemy. It will
   * aim its gun at the enemy robot and then fire a bullet with varying
   * power according to distance of enemy.
   *
   *@param event when our robot scans an enemy robot.
   */
  @Override
  public final void onScannedRobot(final ScannedRobotEvent event) {
    //Always stay at 90 degree angle to the enemy.
    setTurnRight(event.getBearing() + 90 - 30 * directionToMove);

    //If the enemy has a drop in energy of <=3 we assume the
    //enemy has fired so we move.
    double changeInEnergy = energyOfEnemy - event.getEnergy();
    if (changeInEnergy > 0 && changeInEnergy <= 3) {
      //Our robot changes direction and moves closer towards enemy.
      directionToMove = -directionToMove;
      setAhead((event.getDistance() / 4 + 27) * directionToMove);
    }

    //Changes scanDirection so that radar oscillates.
    scanDirection = -scanDirection;
    setTurnRadarRight(360 * scanDirection);

    //Turn gun to face enemy.  Normalize angle so gun
    //turns the shortest distance.
    setTurnGunRight(normalRelativeAngleDegrees(
                    getHeading() - getGunHeading() + event.getBearing()));

    //Update distance with current distance of enemy
    distance = event.getDistance();

    //Fire at target with power varying with distance.
    if (distance < 50 ) {
      fire(2);
    }
    else if (distance < 100) {
      fire(1.5);
    }
    else if (distance < 200) {
      fire(1);
    }
    else {
      fire(0.1);
    }
    //Update the energy level of the enemy.
    energyOfEnemy = event.getEnergy();

  }

  /**
   * OnHitRobot, this robot should change direction.
   *
   * @param event when our robot hits another robot
   */
  @Override
  public final void onHitRobot(final HitRobotEvent event) {
    directionToMove = -directionToMove;
    setAhead(20 * directionToMove);

  }
}
