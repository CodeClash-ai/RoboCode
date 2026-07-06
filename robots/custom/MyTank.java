// CodeClash ladder import
// Source: https://github.com/Luke-F-W/NagiSphere-Games-Fleadh-2026/blob/HEAD/Nagisphere.java
// Author: Luke-F-W   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
 import robocode.*;
 import java.awt.geom.Point2D;
 import java.awt.Color;
 public class MyTank extends Robot {
     double sentryX, sentryY; //The sentrys coords, use these to gemerate the circle & points
     double[][] points; //points on the circle that the bot moves through
     int currentIndex = 7; //is roughly in the center, represents which point
     boolean direction = true; //true = 1 way false means other way
     double goalX, goalY; //where ur going
     double enemyX, enemyY; //opponents coords
     double predictedX; //x to shoot
     double predictedY; //y to shoot
     double bestDist = Double.MAX_VALUE;
     double[][] pointsInner, pointsOuter; // points around the main circle
     int ring = 2; // 1 is inner ring, 2 is middle and 3 is outer ring
     double lastEnemyDistance = Double.MAX_VALUE;
     //when starting
     public void run() {
         setAdjustRadarForGunTurn(true);
         setColors(new Color(109, 163, 90), //body
             new Color(255, 255, 255), //gun
             new Color(255, 255, 255), //radar
             new Color(255, 0, 0), //bullet
             new Color(255, 255, 255) //scan
         );
         while (points == null) { //find sentrybot
             turnRadarRight(360);
         }
         //find closest point on circle and go to it
         for (int i = 0; i < points.length; i++) {
             double dist = Math.hypot(points[i][0] - getX(), points[i][1] - getY());
             double angleToPoint = Math.toDegrees(Math.atan2(points[i][0] - getX(), points[i][1] - getY()));
             double bearingToPoint = normaliseBearing(angleToPoint - getHeading());
             double angleToEnemy = Math.toDegrees(Math.atan2(enemyX - getX(), enemyY - getY()));
             double bearingToEnemy = normaliseBearing(angleToEnemy - getHeading());

             boolean pathBlocked = Math.abs(normaliseBearing(bearingToPoint - bearingToEnemy)) < 20 && lastEnemyDistance < dist;
             if (dist < bestDist && !pathBlocked) {
                 bestDist = dist;
                 currentIndex = i;
             }
         }

         //find first point
         goalX = points[currentIndex][0];
         goalY = points[currentIndex][1];
         turnRight(normaliseBearing(Math.toDegrees(Math.atan2(goalX - getX(), goalY - getY())) - getHeading()));
         ahead(Math.hypot(goalX - getX(), goalY - getY()));
         //loop through all game
         while (true) {
             goToNextPoint();
             turnRadarRight(360);
         }
     }
     //when robots seen
     public void onScannedRobot(ScannedRobotEvent e) {
         if (e.getName().contains("SentryBot")) {
             if (points == null) {
                 double absoluteBearing = getHeading() + e.getBearing();
                 sentryX = getX() + e.getDistance() * Math.sin(Math.toRadians(absoluteBearing));
                 sentryY = getY() + e.getDistance() * Math.cos(Math.toRadians(absoluteBearing));
                 points = getCirclePoints(sentryX, sentryY);
             }
             return;
         }
         //get variables for linear targeting
         double enemyBearing = e.getBearing();
         double enemyDistance = e.getDistance();
         double enemyVelocity = e.getVelocity();
         double enemyHeading = e.getHeading();
         double absoluteBearing = getHeading() + enemyBearing;
         double bulletPower = Math.min(3, Math.max(1.5, 400.0 / enemyDistance));
         double bulletSpeed = 20 - 3 * bulletPower;
         //get enemyCoords
         enemyX = getX() + enemyDistance * Math.sin(Math.toRadians(absoluteBearing));
         enemyY = getY() + enemyDistance * Math.cos(Math.toRadians(absoluteBearing));
         //if not moving, fire at them
         if (e.getVelocity() == 0 && getGunHeat() == 0 && points != null) {
             turnGunRight(normaliseBearing(absoluteBearing - getGunHeading()));
             fire(3);
             return;
         }
         predictedX = enemyX;
         predictedY = enemyY;
         double time = 0;
         //predict for linear targeting
         for (int i = 0; i < 5; i++) {
             predictedX = enemyX + enemyVelocity * time * Math.sin(Math.toRadians(enemyHeading));
             predictedY = enemyY + enemyVelocity * time * Math.cos(Math.toRadians(enemyHeading));
             predictedX = Math.max(18, Math.min(getBattleFieldWidth() - 18, predictedX));
             predictedY = Math.max(18, Math.min(getBattleFieldHeight() - 18, predictedY));
             time = Math.hypot(predictedX - getX(), predictedY - getY()) / bulletSpeed;
         }
         //gun turning
         double gunTurn = normaliseBearing(Math.toDegrees(Math.atan2(predictedX - getX(), predictedY - getY())) - getGunHeading());
         turnGunRight(gunTurn);
         //FIRE AT THEM
         if (getGunHeat() == 0 && Math.abs(gunTurn) < 10) {
             fire(bulletPower);
         }
     }
     //if hit by bullet, move
     public void onHitByBullet(HitByBulletEvent e) {
         double bearing = e.getBearing();
         if ((bearing > -45 && bearing < 45) || (bearing > 135 || bearing < -135)) {
             int newRing = ring;
             while (newRing == ring) {
                 newRing = (int)(Math.random() * 3) + 1;
             }
             ring = newRing;
         }
     }
     //shoot if criteria is met
     public void onHitRobot(HitRobotEvent e) {
         if (e.getName().contains("SentryBot")) {
             return;
         }
         double absoluteBearing = getHeading() + e.getBearing();
         double gunTurn = normaliseBearing(absoluteBearing - getGunHeading());
         turnGunRight(gunTurn);
         if (Math.abs(gunTurn) <= 10) {
             fire(3);
         }
     }
     //if wall is hit, reverse direction and go in the opposite way, more of a failsafe as it should move beforehand
     public void onHitWall(HitWallEvent e) {
         direction = !direction;
     }
     //this takes sentrycoords and calculates circles & points with that
     double[][] getCirclePoints(double x, double y) {
         double[][] pts = new double[37][2];
         pointsInner = new double[37][2];
         pointsOuter = new double[37][2];
         double radius = 540;
         for (int i = 0; i < 37; i++) {
             double angle = (2 * Math.PI / 37) * i;
             pts[i][0] = x + radius * Math.cos(angle);
             pts[i][1] = y + radius * Math.sin(angle);
             pointsInner[i][0] = x + (radius - 30) * Math.cos(angle);
             pointsInner[i][1] = y + (radius - 30) * Math.sin(angle);
             pointsOuter[i][0] = x + (radius + 30) * Math.cos(angle);
             pointsOuter[i][1] = y + (radius + 30) * Math.sin(angle);
         }
         return pts;
     }
     //normalises bearing to between -180 and 180
     double normaliseBearing(double angle) {
         while (angle > 180) angle -= 360;
         while (angle < -180) angle += 360;
         return angle;
     }
     //goes to next point
     private void goToNextPoint() {
         currentIndex = direction ? (currentIndex + 1) % 37 : (currentIndex - 1 + 37) % 37;
         goalX = (ring == 1) ? pointsInner[currentIndex][0] : (ring == 3) ? pointsOuter[currentIndex][0] : points[currentIndex][0];
         goalY = (ring == 1) ? pointsInner[currentIndex][1] : (ring == 3) ? pointsOuter[currentIndex][1] : points[currentIndex][1];

         if (goalX < 45 || goalX > 755 || goalY < 45 || goalY > 755) {
             direction = !direction;
             currentIndex = direction ? (currentIndex + 2) % 37 : (currentIndex - 2 + 37) % 37;
             goalX = (ring == 1) ? pointsInner[currentIndex][0] : (ring == 3) ? pointsOuter[currentIndex][0] : points[currentIndex][0];
             goalY = (ring == 1) ? pointsInner[currentIndex][1] : (ring == 3) ? pointsOuter[currentIndex][1] : points[currentIndex][1];
         }
         turnRight(normaliseBearing(Math.toDegrees(Math.atan2(goalX - getX(), goalY - getY())) - getHeading()));
         ahead(Math.hypot(goalX - getX(), goalY - getY()));
     }
 }
