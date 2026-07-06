// CodeClash ladder import
// Source: https://github.com/muzardo/robocode-trianglehunter/blob/HEAD/Main.java
// Author: muzardo   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.util.Random;

/**
 * MyTank

 * Estratégia:
 *  - Mover-se em triângulos irregulares pela arena
 *  - Inverte direção ao levar tiro
 *  - Escaneia continuamente e atira com potência 1.5
 *  - Recua e muda rota ao bater na parede
 */
public class MyTank extends AdvancedRobot {

    // --- Constantes de movimento ---
    private static final double BASE_SIDE_LENGTH  = 150;  // comprimento base de cada lado (px)
    private static final double BASE_TURN_ANGLE   = 120;  // ângulo base de virada (graus)
    private static final double ANGLE_VARIATION   = 20;   // variação máxima do ângulo (±)
    private static final double LENGTH_VARIATION  = 80;   // variação máxima do comprimento (±)
    private static final double FIRE_POWER        = 1.5;  // potência de disparo

    // --- Estado interno ---
    private int  direction    = 1;   // 1 = sentido horário, -1 = anti-horário
    private int  sidesWalked  = 0;   // quantos lados do triângulo já foram percorridos
    private double currentSideLength;
    private double currentTurnAngle;
    private final Random rng = new Random();

    // --- Alvo atual ---
    private double enemyBearing = 0;
    private boolean hasTarget   = false;

    // =========================================================================
    // CICLO PRINCIPAL
    // =========================================================================
    @Override
    public void run() {
        setupVisuals();

        // Dissocia o radar do canhão e o canhão do corpo — controle independente
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        // Varredura inicial do radar antes de começar a se mover
        setTurnRadarRight(Double.POSITIVE_INFINITY);

        while (true) {
            walkTriangleSide();   // anda um lado do triângulo
            turnToNextSide();     // vira para o próximo lado
        }
    }

    // =========================================================================
    // MOVIMENTO EM TRIÂNGULO
    // =========================================================================

    /** Anda um lado do triângulo com comprimento levemente aleatorizado. */
    private void walkTriangleSide() {
        currentSideLength = BASE_SIDE_LENGTH
                + (rng.nextDouble() * 2 - 1) * LENGTH_VARIATION;

        setAhead(direction * currentSideLength);
        execute(); // dispara o comando e aguarda conclusão
        sidesWalked++;
    }

    /** Vira para o próximo vértice do triângulo com ângulo levemente aleatorizado. */
    private void turnToNextSide() {
        currentTurnAngle = BASE_TURN_ANGLE
                + (rng.nextDouble() * 2 - 1) * ANGLE_VARIATION;

        setTurnRight(direction * currentTurnAngle);
        execute();
    }

    // =========================================================================
    // RADAR
    // =========================================================================

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        // Mantém radar travado no inimigo enquanto ele está visível
        double radarTurn = getHeading() + e.getBearing() - getRadarHeading();
        setTurnRadarRight(Utils.normalRelativeAngleDegrees(radarTurn) * 1.9);

        // Guarda bearing para uso no canhão
        enemyBearing = e.getBearing();
        hasTarget    = true;

        // --- MIRA E ATIRA ---
        aimAndFire(e);
    }

    // =========================================================================
    // ATAQUE
    // =========================================================================

    /**
     * Aponta o canhão para o inimigo e atira.
     * Inclui compensação básica de posição futura (linear prediction).
     */
    private void aimAndFire(ScannedRobotEvent e) {
        double bulletSpeed  = 20 - 3 * FIRE_POWER;
        double enemyDist    = e.getDistance();

        // Tempo estimado para o tiro chegar (ticks)
        long travelTime     = (long) (enemyDist / bulletSpeed);

        // Posição futura aproximada do inimigo
        double enemyAbsAngle = Math.toRadians(getHeading() + e.getBearing());
        double futureX = getX()
                + enemyDist * Math.sin(enemyAbsAngle)
                + travelTime * e.getVelocity() * Math.sin(Math.toRadians(e.getHeading()));
        double futureY = getY()
                + enemyDist * Math.cos(enemyAbsAngle)
                + travelTime * e.getVelocity() * Math.cos(Math.toRadians(e.getHeading()));

        // Ângulo absoluto até a posição futura
        double aimAngle = Math.toDegrees(Math.atan2(futureX - getX(), futureY - getY()));
        double gunTurn  = Utils.normalRelativeAngleDegrees(aimAngle - getGunHeading());

        setTurnGunRight(gunTurn);

        // Só atira se o canhão estiver bem alinhado (≤ 10°)
        if (Math.abs(gunTurn) <= 10) {
            setFire(FIRE_POWER);
        }
    }

    // =========================================================================
    // EVENTOS DE DANO / COLISÃO
    // =========================================================================

    /** Ao ser atingido: inverte direção e para o movimento atual. */
    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        direction *= -1;           // inverte horário ↔ anti-horário
        stop();                    // cancela movimento pendente
        setAhead(direction * 50);  // pequeno recuo imediato
        execute();
    }

    /** Ao bater na parede: recua, muda rota e reinicia o triângulo. */
    @Override
    public void onHitWall(HitWallEvent e) {
        stop();
        setBack(direction * 60);
        setTurnRight(direction * (currentTurnAngle + rng.nextInt(30)));
        sidesWalked = 0;   // reinicia o triângulo
        execute();
    }

    /** Ao colidir com outro robô: afasta e atira. */
    @Override
    public void onHitRobot(HitRobotEvent e) {
        if (e.isMyFault()) {
            setBack(direction * 40);
        }
        setFire(3.0); // tiro de emergência máximo na colisão
        execute();
    }

    // =========================================================================
    // VISUAL (opcional — apenas estética)
    // =========================================================================

    private void setupVisuals() {
        setBodyColor(new Color(60, 20, 20));     // azul-marinho escuro
        setGunColor(new Color(255, 0, 0));      // ciano neon
        setRadarColor(new Color(255, 255, 255));     // laranja
        setBulletColor(new Color(255, 0, 0));   // verde elétrico
        setScanColor(new Color(255, 0, 0, 80)); // amarelo translúcido
    }
}