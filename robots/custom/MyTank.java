package custom;

import robocode.*;
import java.awt.geom.Point2D;
import java.awt.Color;

/**
 * MyTank - improved AdvancedRobot bot.
 *
 * Strategy:
 *  - Radar: lock on to the last scanned enemy and keep sweeping to reacquire quickly.
 *  - Gun: linear-prediction targeting (assume enemy continues at current velocity/heading),
 *         with power scaled by distance (more power up close, less far away to save energy).
 *  - Movement: perpendicular "orbit" strafing around the enemy at a preferred distance,
 *         with periodic randomized direction reversals to avoid simple targeting bots,
 *         plus wall-avoidance so we don't get stuck in a corner.
 *  - Defensive: on being hit, reverse/change strafing direction.
 *
 * This bot only relies on our own AdvancedRobot API usage; it doesn't assume anything
 * about the opponent's implementation.
 */
public class MyTank extends AdvancedRobot {

    // Preferred distance to keep from the enemy while orbiting.
    private static final double PREFERRED_DISTANCE = 300;

    // Strafing direction: 1 or -1. Flips periodically / when hit.
    private int moveDirection = 1;

    // Turn counter used to occasionally flip strafing direction to be less predictable.
    private int strafeTimer = 0;

    // Last known enemy info, used for linear targeting and for continuing to track
    // even in ticks where we don't get a fresh scan.
    private double enemyDistance = Double.MAX_VALUE;

    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.RED, Color.BLACK, Color.YELLOW);

        // Kick off radar spinning; onScannedRobot will keep re-aiming it at the enemy.
        setTurnRadarRight(Double.POSITIVE_INFINITY);

        while (true) {
            // If we haven't seen anyone in a while, keep spinning radar to find them.
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double absBearing = getHeadingRadians() + e.getBearingRadians();
        enemyDistance = e.getDistance();

        // --- Radar lock: point radar straight at enemy plus a little extra sweep so we
        // don't lose lock if it moves fast. ---
        double radarTurn = robocode.util.Utils.normalRelativeAngle(
                absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 1.5);

        // --- Gun: linear prediction targeting ---
        double bulletPower = bulletPowerForDistance(e.getDistance());
        double bulletSpeed = 20 - 3 * bulletPower;

        // Predict enemy future position assuming constant velocity/heading.
        double enemyHeading = e.getHeadingRadians();
        double enemyVelocity = e.getVelocity();

        double myX = getX();
        double myY = getY();
        double enemyX = myX + Math.sin(absBearing) * e.getDistance();
        double enemyY = myY + Math.cos(absBearing) * e.getDistance();

        double predictedX = enemyX;
        double predictedY = enemyY;
        double t = 0;
        // Iteratively refine time-to-target using predicted position.
        for (int i = 0; i < 5; i++) {
            double dist = Point2D.distance(myX, myY, predictedX, predictedY);
            t = dist / bulletSpeed;
            predictedX = enemyX + Math.sin(enemyHeading) * enemyVelocity * t;
            predictedY = enemyY + Math.cos(enemyHeading) * enemyVelocity * t;
        }

        // Clamp predicted position to inside the battlefield so we don't aim off-field.
        predictedX = Math.min(Math.max(predictedX, 18), getBattleFieldWidth() - 18);
        predictedY = Math.min(Math.max(predictedY, 18), getBattleFieldHeight() - 18);

        double gunTargetAngle = Math.atan2(predictedX - myX, predictedY - myY);
        double gunTurn = robocode.util.Utils.normalRelativeAngle(
                gunTargetAngle - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        if (getGunHeat() == 0 && Math.abs(gunTurn) < 0.2) {
            setFire(bulletPower);
        }

        // --- Movement: orbit / strafe around the enemy at preferred distance ---
        strafeTimer++;
        if (strafeTimer > 40 + (int) (Math.random() * 30)) {
            strafeTimer = 0;
            if (Math.random() < 0.4) {
                moveDirection = -moveDirection;
            }
        }

        double distanceError = e.getDistance() - PREFERRED_DISTANCE;
        // Move perpendicular to enemy bearing (strafe), plus adjust toward/away
        // from enemy to hold preferred distance.
        double perpendicularAngle = absBearing + (Math.PI / 2) * moveDirection;
        double radialAdjust = (distanceError > 0) ? 0 : Math.PI; // move toward if too far, away if too close
        // Blend: mostly perpendicular, slight radial correction handled via move distance sign.

        double moveAngle = perpendicularAngle;
        double turnToMove = robocode.util.Utils.normalRelativeAngle(
                moveAngle - getHeadingRadians());

        setTurnRightRadians(turnToMove);

        double moveAmount = 100;
        if (Math.abs(distanceError) > 50) {
            // Bias speed slightly to correct distance while still strafing.
            moveAmount = (distanceError > 0) ? 120 : 80;
        }
        setAhead(moveAmount);

        execute();
    }

    private double bulletPowerForDistance(double distance) {
        if (distance < 150) {
            return 3.0;
        } else if (distance < 300) {
            return 2.2;
        } else if (distance < 500) {
            return 1.5;
        }
        return 1.0;
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Reverse strafing direction and juke.
        moveDirection = -moveDirection;
        strafeTimer = 0;
        setTurnRightRadians(robocode.util.Utils.normalRelativeAngle(
                Math.PI / 2 - e.getBearingRadians()));
        setAhead(60 * moveDirection);
        execute();
    }

    public void onHitWall(HitWallEvent e) {
        // Back off from the wall and reverse direction.
        moveDirection = -moveDirection;
        setBack(80);
        execute();
    }

    public void onHitRobot(HitRobotEvent e) {
        // Rammed something - back away.
        setBack(60);
        execute();
    }
}
