// CodeClash ladder import
// Source: https://github.com/UR4N0-235/UR4NO/blob/HEAD/UR4NO.java
// Author: UR4N0-235   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
/**
 * MyTank - a robot by ( Matheus Fernandes )
 *
 * a seguir, pessouas que ajudaram muito no desenvolvimento desse robozinho
 *
 * Inspired by {
 *	  Voidios: CARA INCRIVEL, MARAVILHOSO ME ENSINOU MUITTOOOO - https://robowiki.net/wiki/Diamond
 *    Skilgannon: um cara muito foda, criou 2 robos increveis e DOMINA no 1v1 e melee, god D+ https://robowiki.net/wiki/DrussGT
 *    Nat: usuaria do robowiki, muito bacana e gentil, uma fofa - https://robowiki.net/wiki/User:Nat
 *    PEZ: um super usuario do robowiki, responsavel por varios tutoriais, god d+ - https://robowiki.net/wiki/User:PEZ
 *    ABC: tem nem oque falar, melhores tutoriais e exemplos de codigo, percursor da wave surfing
 *	}
 *
 * Usando pacotes personalizados de {
 *		Nat, David Alves - para as funcoes de desenho do debugger - package ur4n0.graph.BHDrawer
 * }
 */

package custom;


import robocode.BulletHitEvent;
import robocode.HitByBulletEvent;
import robocode.AdvancedRobot;
import robocode.DeathEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.SkippedTurnEvent;
import robocode.WinEvent;

import java.awt.Graphics2D;


public class MyTank extends AdvancedRobot {

protected UranoSkins _skin;
protected static UranoEye _radar;
protected static UranoWhoosh _move;
protected static UranoDCGun _gun;

// public static enem

// para testar funcionalidades
// se = false ==> robo nao move :: nao atira
private static final boolean _doMove = true;
private static final boolean _doShot = true;

public void run() {
        initComponents();
        _skin.choiceColor();

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while(true) {
                // movimento so acontece em caso de deteccao de balas
                // if(_doMove) {
                //         _move.execute();
                // }
                if(_doShot) {
                        _gun.execute();
                }
                _radar.execute();

                execute();
        }

}

public void initComponents(){

        if(_skin == null) {
                _skin = new UranoSkins(this);
        }

        if(_radar == null) {
                _radar = new UranoEye(this);
        }

        if(_doMove && _move == null) {
                _move = new UranoWhoosh(this);
        }

        if(_gun == null) {
                _gun = new UranoDCGun(this);

                if(!_doShot) {
                        _gun.maxBulletPower = 0;
                        _gun.minBulletPower = 0;
                }
        }

        _gun.initNewRound();
        _radar.initNewRound();
}

public void onScannedRobot(ScannedRobotEvent e) {
        if (_doMove) {
                _move.onScannedRobot(e);
        }
        if (_doShot) {
                _gun.update(e);
        }
        _radar.onScannedRobot(e);
}

public void onHitByBullet(HitByBulletEvent e) {
        if(_doMove) {
                _move.onHitByBullet(e);
        }
}

public void onBulletHit(BulletHitEvent e) {
        _gun.update(e);
}

public void onWin(WinEvent e) {
}

public void onDeath(DeathEvent e) {
        if (getOthers() > 0) {
                _gun.cleanUpRound();
        }
}

public void onPaint(Graphics2D g) {
}

public void onSkippedTurn(SkippedTurnEvent e) {
        System.out.println("WARNING: Turn skipped at: " + e.getTime());
}

public void onHitWall(HitWallEvent e) {
        System.out.println("WARNING: I hit a wall (" + getTime() + ").");
}
}
