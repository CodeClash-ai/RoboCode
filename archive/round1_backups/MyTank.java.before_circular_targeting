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
 *         wall-avoidance (clamp target waypoint to a safe inset rectangle so we never
 *         plan a move that drives us into a wall), plus an explicit stuck-watchdog that
 *         forces a strong escape maneuver if our velocity has been ~0 for too long.
 *  - Defensive: on being hit, reverse/change strafing direction.
 *
 * IMPORTANT BUGFIX (see README_agent.md "wall-stuck bug" writeup): analysis of
 * round-0 match logs showed that in EVERY losing game, our tank got physically
 * wedged against a wall (x/y frozen for 700+ consecutive turns) because the old
 * onScannedRobot() movement logic had NO wall-avoidance at all -- it would happily
 * keep commanding "ahead" straight into a wall every single scan (which happens
 * nearly every turn once the enemy is visible), fighting against onHitWall()'s
 * one-shot setBack(80) recovery and effectively canceling out into a near-zero-net
 * -movement standoff. While frozen like this, both robots' energy drains steadily
 * every turn (looks like the environment's anti-stalemate/inactivity decay), and
 * whichever robot had less energy banked before the freeze started loses that race.
 * This rewrite fixes it at the source: every planned move is clamped to a safe
 * inset rectangle of the battlefield *before* being issued, and a watchdog detects
 * near-zero velocity over several consecutive scans and forces a hard escape burn
 * toward the field center, breaking any wall standoff quickly instead of letting it
 * drag on for hundreds of turns.
 *
 * This bot only relies on our own AdvancedRobot API usage; it doesn't assume anything
 * about the opponent's implementation.
 */
public class MyTank extends AdvancedRobot {

    // Preferred distance to keep from the enemy while orbiting.
    private static final double PREFERRED_DISTANCE = 300;

    // Keep planned waypoints at least this far from any wall.
    private static final double WALL_MARGIN = 70;

    // Strafing direction: 1 or -1. Flips periodically / when hit.
    private int moveDirection = 1;

    // Turn counter used to occasionally flip strafing direction to be less predictable.
    private int strafeTimer = 0;

    // Last known enemy info, used for linear targeting and for continuing to track
    // even in ticks where we don't get a fresh scan.
    private double enemyDistance = Double.MAX_VALUE;

    // Turn number when we last saw an enemy; used to trigger fallback "search"
    // movement if we haven't scanned anyone for a while (e.g. radar temporarily
    // lost lock, or enemy is outside our current sweep). Prevents us from being
    // a stationary sitting duck if onScannedRobot stops firing for any reason.
    private long lastScanTime = -1000;
    private int searchTurnDir = 1;

    // Stuck-watchdog: counts consecutive onScannedRobot calls where our velocity
    // has stayed near zero despite us commanding movement. If this gets too high,
    // we're probably wedged against a wall (or another robot) and need to force a
    // strong escape maneuver rather than keep doing the same thing.
    private int stuckScanCount = 0;

    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.RED, Color.BLACK, Color.YELLOW);

        // Kick off radar spinning; onScannedRobot will keep re-aiming it at the enemy.
        setTurnRadarRight(Double.POSITIVE_INFINITY);

        while (true) {
            // CRITICAL RADAR-STALL FIX (see README_agent.md "radar freeze" writeup):
            // onScannedRobot() takes over the radar every time it fires, issuing a
            // small precise "lock on" turn (setTurnRadarRightRadians(radarTurn * 1.5)).
            // If that lock-on turn ever completes with the enemy no longer inside the
            // radar's arc for the next tick (e.g. we turned our body away, the enemy
            // sped off, or the lock math returned ~0 turn), NOTHING then re-issues a
            // fresh sweep -- the one-time setTurnRadarRight(INFINITY) call before this
            // loop only ever fires once, at t=0, and gets permanently overwritten the
            // very first time onScannedRobot runs. Once the radar's pending turn
            // reaches zero with no enemy in view, it just sits frozen forever: no more
            // scans, no more firing, while both robots quietly bleed energy from the
            // engine's inactivity decay. (Confirmed via log analysis of round 1's two
            // losses: our radar heading (rh) froze permanently mid-game while our body
            // kept moving, and we simply stopped firing for the rest of a 1000+ turn
            // match, losing an attrition race we should have won outright since our
            // opponent literally never moved or fired a shot either time.)
            //
            // Fix: every turn, if the radar currently has no pending turn at all
            // (getRadarTurnRemaining() == 0 -- i.e. any previous command, whether our
            // own lock-on or a stale leftover, has fully completed), unconditionally
            // re-issue an infinite spin. onScannedRobot's precise lock-on command will
            // immediately override this again on any turn we actually see the enemy,
            // so this is a pure safety net that costs nothing while actively tracking
            // and guarantees we always resume sweeping within a single tick of ever
            // losing the target, instead of potentially freezing for the rest of the
            // match.
            if (getRadarTurnRemaining() == 0) {
                setTurnRadarRight(Double.POSITIVE_INFINITY);
            }

            // Fallback "search" behavior: if we haven't scanned an enemy in a while
            // (e.g. right at the start of the round, or if we temporarily lose lock),
            // move around instead of sitting still. This makes us harder to hit and
            // helps us find enemies faster than standing in one spot spinning radar.
            if (getTime() - lastScanTime > 15 && getDistanceRemaining() == 0 && getTurnRemaining() == 0) {
                if (Math.random() < 0.15) {
                    searchTurnDir = -searchTurnDir;
                }
                setTurnRight(30 * searchTurnDir);
                setAhead(120);
            }
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        lastScanTime = getTime();
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

        // --- Stuck watchdog: if our velocity has been ~0 for several consecutive
        // scans (and we're not brand new / just started), we're probably wedged
        // against a wall or another robot. Force a strong escape burn toward the
        // field center instead of continuing normal orbit logic, which is exactly
        // the situation that got us stuck in the first place. ---
        if (Math.abs(getVelocity()) < 0.5) {
            stuckScanCount++;
        } else {
            stuckScanCount = 0;
        }

        if (stuckScanCount > 4) {
            double cx = getBattleFieldWidth() / 2.0;
            double cy = getBattleFieldHeight() / 2.0;
            double angleToCenter = Math.atan2(cx - myX, cy - myY);
            double turnToCenter = robocode.util.Utils.normalRelativeAngle(
                    angleToCenter - getHeadingRadians());
            setTurnRightRadians(turnToCenter);
            setAhead(150);
            // Also flip strafe direction so once we break free we don't immediately
            // re-drive into the same wall.
            moveDirection = -moveDirection;
            strafeTimer = 0;
            execute();
            return;
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

        double moveAmount = 100;
        if (Math.abs(distanceError) > 50) {
            // Bias speed slightly to correct distance while still strafing.
            moveAmount = (distanceError > 0) ? 120 : 80;
        }

        // Project the planned waypoint and clamp it to a safe inset rectangle so we
        // never issue a move that would drive us straight into (or through) a wall.
        // This is the core fix for the "wall standoff" bug: previously we always
        // moved along perpendicularAngle regardless of what was there, which could
        // repeatedly ram a wall and fight against onHitWall()'s recovery forever.
        double targetX = myX + Math.sin(perpendicularAngle) * moveAmount;
        double targetY = myY + Math.cos(perpendicularAngle) * moveAmount;

        double minX = WALL_MARGIN;
        double maxX = getBattleFieldWidth() - WALL_MARGIN;
        double minY = WALL_MARGIN;
        double maxY = getBattleFieldHeight() - WALL_MARGIN;

        boolean clamped = false;
        if (targetX < minX) { targetX = minX; clamped = true; }
        if (targetX > maxX) { targetX = maxX; clamped = true; }
        if (targetY < minY) { targetY = minY; clamped = true; }
        if (targetY > maxY) { targetY = maxY; clamped = true; }

        double moveAngle;
        double finalMoveAmount;
        if (clamped) {
            // Re-derive heading/distance toward the clamped (safe) waypoint instead
            // of blindly driving perpendicular into the wall.
            double dx = targetX - myX;
            double dy = targetY - myY;
            double distToWaypoint = Math.hypot(dx, dy);
            if (distToWaypoint < 10) {
                // We're already basically at the safe boundary; head toward the
                // field center instead so we don't just sit here.
                double cx = getBattleFieldWidth() / 2.0;
                double cy = getBattleFieldHeight() / 2.0;
                moveAngle = Math.atan2(cx - myX, cy - myY);
                finalMoveAmount = 100;
            } else {
                moveAngle = Math.atan2(dx, dy);
                finalMoveAmount = Math.min(distToWaypoint, moveAmount);
            }
        } else {
            moveAngle = perpendicularAngle;
            finalMoveAmount = moveAmount;
        }

        double turnToMove = robocode.util.Utils.normalRelativeAngle(
                moveAngle - getHeadingRadians());
        setTurnRightRadians(turnToMove);
        setAhead(finalMoveAmount);

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
        // Back off from the wall and reverse direction. Head toward the field
        // center rather than just "back()" along our current heading, since that
        // heading might not actually point away from the wall we just hit -- this
        // was part of why the old handler could get stuck fighting onScannedRobot's
        // wall-agnostic movement forever.
        moveDirection = -moveDirection;
        strafeTimer = 0;
        double cx = getBattleFieldWidth() / 2.0;
        double cy = getBattleFieldHeight() / 2.0;
        double angleToCenter = Math.atan2(cx - getX(), cy - getY());
        setTurnRightRadians(robocode.util.Utils.normalRelativeAngle(
                angleToCenter - getHeadingRadians()));
        setAhead(100);
        execute();
    }

    public void onHitRobot(HitRobotEvent e) {
        // Rammed something - back away.
        setBack(60);
        execute();
    }
}
