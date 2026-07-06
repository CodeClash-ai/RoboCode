// CodeClash ladder import
// Source: https://github.com/kinnla/misc/blob/HEAD/robocode/AntiWalls.java
// Author: kinnla   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
/**
 * Copyright (c) 2001-2017 Mathew A. Nelson and Robocode contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://robocode.sourceforge.net/license/epl-v10.html
 */
package custom;

import robocode.*;
import java.awt.*;

/**
 * AntiWalls - a sample robot by kinnla,
 * winning approx. 74% of the score vs. Wall
 */
public class MyTank extends RateControlRobot {

	boolean peek;

	public void run() {
		setBodyColor(Color.orange);
		setGunColor(Color.orange);
		setRadarColor(Color.orange);
		setBulletColor(Color.orange);
		setScanColor(Color.orange);

		peek = false;

		turnLeft(getHeading() % 90);
		move();
		turnGunLeft(135);
		turnRight(90);

		while (true) {
			scan();
			if (peek) {
				move();
				turnRight(90);
				ahead(50);
				peek = false;
			}
		}
	}

	public void onScannedRobot(ScannedRobotEvent e) {
		if (e.getDistance() < 5 * getWidth()) {
			peek = true;
			fire(3);
		}
	}

	private void move() {
		if (Math.abs(getHeading()) < 10) {
			ahead(getBattleFieldHeight() - getY() - 2 * getWidth());
		} else if (Math.abs(180 - getHeading()) < 10) {
			ahead(getY() - 2 * getWidth());
		} else if (Math.abs(90 - getHeading()) < 10) {
			ahead(getBattleFieldWidth() - getX() - 2 * getWidth());
		} else {
			ahead(getX() - 2 * getWidth());
		}
	}
}
