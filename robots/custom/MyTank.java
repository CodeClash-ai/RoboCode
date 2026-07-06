// CodeClash ladder import
// Source: https://github.com/gabriel-lw/QuadWall_robocode/blob/HEAD/quadwall/QuadWall.java
// Author: gabriel-lw   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank.
package custom;
import robocode.*;
import static robocode.util.Utils.normalRelativeAngleDegrees;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;
import java.util.ArrayList;
import robocode.HitRobotEvent;
import robocode.util.Utils;
//import java.awt.Color;

// API help : https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html

/**
 * MyTank - a robot by gabriel-lw, ViniDefreyn and angeloyuna
 */
public class MyTank extends AdvancedRobot
{
	double fieldWidth;
	double fieldHeight;
	double robotsLeft;
//	double[][][] trackList =  new double[3][8][2];
	double[][][][] trackList = new double[3][4][3][2];
	double[][] trackQuad = new double[3][2]; 
	int borderLV = 0;
	int idCurrentQuad =0;
	int idCurrentTrack =-1;
	int idNextTrack =0; //referente a trackList, salva o quadrante atual em relaçao a todos
	int nextQuadTrackId; //referente a trackQuad, salva o trilho atual dentro de um quadrante especifico
	int aimQuadrant = 0;
	int turns = 0; // contar turnos como vezes que mudou de track
	double nextX;
	double nextY;
	double DR = 50; //distancia razoavel da borda que nao permite passagem de inimigos
	double robotEnergy;
	double robotX;
	double robotY;
	double robotAng;
	double gunAng;
	double radarAng;
	double limitLRadar = 90;
	double limitRRadar = 270;
	int radarSentido =1;
	boolean targetingEnemy = false;
	String nearestEnemy = "nomeeeee";
	double nearestEnemyDistance = 999999;
	//pegar o x e y do ponto da borda para ir e ajudar o radar para direçao do movimento nesse primeiro momento
	
	//precisa de um metodo defineRadarLimits que ve a borda mais proxima e faz os 4 casos
	//lembrar do caso 270 e 90 onde tem a quebra
	
	// definir uma variavel de controle para impedir de rotacionar se estiver caçand um inimigo
	//se a distancia aumentar definir novamente para false 
	// definir para false quando chegar em um novo ponto onde escaneia dnv
	


	//fazer as varreduras de reconhecimento apenas nos quadrantes pares 0 ou 2.  o 1 eh as quinas, seria90 e nao 180, complicado

	int sentido =1; //sentido indo ou voltando da trackQuad
	
	//inverter a logica, fazer eixo de movimento, e nao a parede
	boolean isOnHorizontalBorder;
	boolean isOnVerticalBorder;
	
	boolean changeCurrentQuadrant = false;
	boolean recentCollision = false;
	boolean waitRotation =false;
	boolean scanQuadrants = false;
	String lastFiredName= " ";
	int fireConfidence=0;
	int missedBullets = 0;

	// Inicializa as variáveis da quantidade de robôs em cada quadrante e a lista dos robôs já escaneados
	double numFirstQuadrant = 0;
	double numSecondQuadrant = 0;
	double numThirdQuadrant = 0;
	double numFourthQuadrant = 0;
	

	ArrayList<String> names = new ArrayList<>();
	ArrayList<String> firstQuadrantNames = new ArrayList<>();
	ArrayList<String> secondQuadrantNames = new ArrayList<>();
	ArrayList<String> thirdQuadrantNames = new ArrayList<>();
	ArrayList<String> fourthQuadrantNames= new ArrayList<>();

	// Método que direciona o robô a uma certa quantidade dependendo de seu ângulo
	public void turn(double turn1, double turn2,  double turn3, double turn4) {
			
		if (getHeading() == 0) {
			setTurnRight(turn1);
		} else if (getHeading() == 90) {
			setTurnRight(turn2);
		} else if (getHeading() == 180) {
			setTurnRight(turn3);
		} else {
			setTurnRight(turn4);
		}
	}

	// Método que escaneia o quadrante do oponente escaneado
	public void scanOpponentQuadrant(ScannedRobotEvent e) {

		double opponentDistance, opponentX, opponentY, quadrantBorderY, quadrantBorderX;
		double a, b;
		
		// Cálcula as coordenadas do oponente escaneado
		
		opponentDistance = e.getDistance(); 		
		
		a = Math.sin(Math.toRadians(getHeading() + e.getBearing())) * opponentDistance;
		b = Math.cos(Math.toRadians(getHeading() + e.getBearing())) * opponentDistance;
		
		opponentX = Math.abs(getX() + a);
		opponentY = Math.abs(getY() + b);
	
		// Define as bordas de cada quadrante
		quadrantBorderX = getBattleFieldWidth() / 2;
		quadrantBorderY = getBattleFieldHeight() / 2;

		// Se robô oponente já foi escaneado, printar essa mensagem
		//essa condiçao nao deve ser aqui
		if (names.contains(e.getName())) {
			System.out.println("Robô já contado.");
		}
		
		// Se robô oponente já não foi escaneado, verificar qual quadrante ele está
		else {
		
			// Se oponente robô estiver no primeiro quadrante
			if (opponentX < quadrantBorderX &&  opponentY > quadrantBorderY) {
				numFirstQuadrant += +1;
				System.out.println("Robôs no primeiro quadrante: " + numFirstQuadrant);
				firstQuadrantNames.add(e.getName());
			
				if (secondQuadrantNames.contains(e.getName())) {
					secondQuadrantNames.remove(e.getName());
				}
				
				else if (thirdQuadrantNames.contains(e.getName())) {
					thirdQuadrantNames.remove(e.getName());
				}
				
				else if (fourthQuadrantNames.contains(e.getName())) {
					fourthQuadrantNames.remove(e.getName());
				}			
				
			}
			
		
			// Se oponente robô estiver no segundo quadrante
			else if (opponentX > quadrantBorderX && opponentY > quadrantBorderY) {
				numSecondQuadrant += +1;
				System.out.println("Robôs no segundo quadrante: " + numSecondQuadrant);	
				secondQuadrantNames.add(e.getName());
			
				if (firstQuadrantNames.contains(e.getName())) {
					firstQuadrantNames.remove(e.getName());
				}
				
				else if (thirdQuadrantNames.contains(e.getName())) {
					thirdQuadrantNames.remove(e.getName());
				}
				
				else if (fourthQuadrantNames.contains(e.getName())) {
					fourthQuadrantNames.remove(e.getName());
				}

			}	

			// Se oponente robô estiver no terceiro quadrante
			else if (opponentX < quadrantBorderX && opponentY < quadrantBorderY) {
				numThirdQuadrant += +1;
				System.out.println("Robôs no terceiro quadrante: " + numThirdQuadrant);
				thirdQuadrantNames.add(e.getName());
			
				if (firstQuadrantNames.contains(e.getName())) {
					firstQuadrantNames.remove(e.getName());
				}
					
				else if (secondQuadrantNames.contains(e.getName())) {
					secondQuadrantNames.remove(e.getName());
				}
				
				else if (thirdQuadrantNames.contains(e.getName())) {
					fourthQuadrantNames.remove(e.getName());
				}
				
			}
		
			// Se oponente robô estiver no quarto quadrante
			else if (opponentX > quadrantBorderX && opponentY < quadrantBorderY) {
				numFourthQuadrant += +1;
				System.out.println("Robôs no quarto quadrante: " + numFourthQuadrant);
				fourthQuadrantNames.add(e.getName());
				
				if (firstQuadrantNames.contains(e.getName())) {
					firstQuadrantNames.remove(e.getName());
				}
				
				else if (secondQuadrantNames.contains(e.getName())) {
					secondQuadrantNames.remove(e.getName());
				}
				
				else if (thirdQuadrantNames.contains(e.getName())) {
					thirdQuadrantNames.remove(e.getName());
				}
				
			}
			names.add(e.getName());
		}	
	}

	public void scanNextQuadrant() {

			numFirstQuadrant = 0;
			numSecondQuadrant = 0;
			numThirdQuadrant = 0;
			numFourthQuadrant = 0;
			names.clear();
	
			turnGunRight(360);

			if ((numFirstQuadrant > numSecondQuadrant) && (numFirstQuadrant > numThirdQuadrant) && (numFirstQuadrant > numFourthQuadrant)) {
				System.out.println("Quadrante a se mover: Primeiro");
				/// entrar no subarray correspondente ao quadrantre, analisar as 3 posicoes e achar a mais proxima da atual
				// nao permitir movimentaçao entre quadrantes nao adjacentes 
				//caso em que nao é adjacente: verificar o anterior e o posterior do quadrante atual
				//nao achando correspondencia tanto faz para qual caminho seguir, a distancia é a mesma
				// escolhe um dos quadrantes intermediarios e segue para o de objetivo 
				aimQuadrant = 1;
				

			} else if ((numSecondQuadrant > numThirdQuadrant) && (numSecondQuadrant > numFourthQuadrant)) {
				System.out.println("Quadrante a se mover: Segundo");
				aimQuadrant =2;

			} else if (numThirdQuadrant > numFourthQuadrant) {
				System.out.println("Quadrante a se mover: Terceiro");	
				aimQuadrant =3;		

			} else {
				System.out.println("Quadrante a se mover: Quarto");
				aimQuadrant = 0;
			}
			scanQuadrants =false;
			
			//setTurnGunLeft(90);
	}
	
	/**
	 * run: MyTank's default behavior
	 */	
	public void run() {
	
			waitRotation =true;
			robotX = getX();
			robotY = getY();
			robotAng = getHeading();
			fieldWidth = getBattleFieldWidth();
			fieldHeight = getBattleFieldHeight();
			
			
			//inicializa os tracks
			setTrackList();
		   
			double moveDistance;
			double distanceWestWall, distanceEastWall, distanceSouthWall, distanceNorthWall;
				
			distanceWestWall = getX();
			distanceEastWall = getBattleFieldWidth() - getX();
			distanceSouthWall = getY();
			distanceNorthWall = getBattleFieldHeight() - getY();

			// Se estiver mais próximo da borda oeste, direcionar para ela
			if (distanceWestWall < distanceEastWall && distanceWestWall < distanceNorthWall && distanceWestWall < distanceSouthWall) {
				setTurnLeft(getHeading() % 90);
				turn(-90, 180, 90, 0);
				moveDistance = distanceWestWall -50;
				nextX = robotX - moveDistance;
				nextY = robotY;
				//definir o ponto de inicio do track de acordo com a borda
				if(distanceSouthWall <= distanceNorthWall )
				{
					//quad 0
					idCurrentQuad =0;
					//vai para oeste sul, quad 3  ids 1, 0 , 7    
					
				}else{
					//vai para oeste norte
					//quad 1 ids 1, 2 ,3 
					idCurrentQuad =1;
					
					
				}
				
			}
			
			// Se estiver mais próximo da borda leste, direcionar para ela
			else if (distanceEastWall < distanceWestWall && distanceEastWall < distanceNorthWall && distanceEastWall < distanceSouthWall) {
				setTurnLeft(getHeading() % 90);
				turn(90, 0, -90, 180);
				moveDistance = distanceEastWall -50;
				nextX = robotX + moveDistance;
				nextY = robotY;
				
				if(distanceSouthWall <= distanceNorthWall )
				{
					idCurrentQuad = 3;
					//vai para leste sul, quad 2  ids 7, 6 , 5
					
				}else{
					idCurrentQuad = 2;
					//vai para leste norte, quad 2 ids 5, 4 ,3 
			
					
				}

				
			
			}
			
			// Se estiver mais próximo da borda norte, direcionar para ela
			else if (distanceNorthWall < distanceSouthWall && distanceNorthWall < distanceEastWall && distanceNorthWall < distanceWestWall) {
				setTurnLeft(getHeading() % 90);
				turn(0, -90, 180, 90);
				moveDistance = distanceNorthWall -50;
				nextY = robotY + moveDistance;
				nextX = robotX;
				
				if(distanceWestWall <= distanceEastWall )
				{
					idCurrentQuad = 1;
					//vai para norte oeste, quad 0  ids 3, 2 , 1     
					
				}else{
					//quad 1 ids 3, 4 ,5 
					//vai para norte leste
					idCurrentQuad = 2;
				
				}
				
			}
			
			// Se estiver mais próximo da borda sul, direcionar para ela
			else {
				setTurnLeft(getHeading() % 90);
				turn(180, 90, 0, -90);
				moveDistance = distanceSouthWall -50;
				nextY = robotY - moveDistance;
				nextX = robotX;
				
				if(distanceWestWall <= distanceEastWall )
				{
					//vai para oeste sul, quad 3  ids 7, 0 , 1   
					idCurrentQuad = 0;
					
				}else{
					idCurrentQuad = 3;
				}
			}
			

			//setAhead(moveDistance);
			execute();
			//setAdjustGunForRobotTurn(true);

			setTurnRight(90);

			setTurnGunRight(180);
			setTrackList();
			scanNextQuadrant();
			aimQuadrant = idCurrentQuad;
			changeCurrentQuadrant= true;
		
			System.out.println("currQuad:" +idCurrentQuad +"  currTrack:" + idCurrentTrack+ "   nextTrack:"+idNextTrack+ "    nextQuadTrack:" +nextQuadTrackId+ "     aimQuadrant:" +aimQuadrant);
			

		


			int contagem = 0;

			//getNextPosition();

			// Necessário implementar uma solução nos casos em que o robô se colide com um robô ao se mover para uma borda
			
		// Robot main loop
		while(true) {
		
			double quadrantBorderX = getBattleFieldWidth() / 2;
			boolean nearSouthWall = getY() <= 65;
			boolean nearNorthWall = getBattleFieldHeight() - getY() <= 65;


			if ((getX() < quadrantBorderX + 2) && (getX() > quadrantBorderX - 2) && (nearSouthWall || nearNorthWall))
			{	
				scanQuadrants = true;
				turns = 0;
				scanNextQuadrant();
			}
			
		// por algum motivo da problema
		//	if(!targetingEnemy){
				//chamar o metodo de incremento do radar
			else {
				scanLimits();
				System.out.println("currQuad:" +idCurrentQuad +"  currTrack:" + idCurrentTrack+ "   nextTrack:"+idNextTrack+ "    nextQuadTrack:" +nextQuadTrackId+ "     aimQuadrant:" +aimQuadrant);
			}
				
		//	}

		/*	if(contagem ==5){
				aimQuadrant = 3;
			}
			else if(contagem == 20){
				aimQuadrant = 1;
			}
			else if(contagem == 35){
				aimQuadrant = 0;
			}
			else if(contagem == 45){
				aimQuadrant = 1;
			}
			else if(contagem == 60){
				aimQuadrant = 3;
			}
			else if(contagem == 70){
				aimQuadrant = 2;
			}
			else if(contagem == 80){
				aimQuadrant = 3;
			}
			else if(contagem == 90){
				aimQuadrant = 1;
			}
			else if(contagem >= 100){
				aimQuadrant = 0;
				System.out.println("ACABOUU#########################: ");
				
			}*/
			
			if(idCurrentQuad != aimQuadrant){
				changeCurrentQuadrant = true;
			}
			
			robotX = getX();
			robotY = getY();
			robotAng = getHeading();
		///	robotEnergy = 
			gunAng = getGunHeading();
			radarAng = getRadarHeading();
			robotsLeft = getOthers();
			calcBorderExis();
			decideTrack();
			System.out.println("currQuad:" +idCurrentQuad +"  currTrack:" + idCurrentTrack+ "   nextTrack:"+idNextTrack+ "    aimQuadrant:" +aimQuadrant);
			
			
			//como o movimento esta definido pela distancia exata do nextX e nextY, a margem de erro nao influencia mais a distancia
			if(robotX >= nextX-45 && robotX<= nextX+45 && robotY >= nextY-45 && robotY <= nextY+45)
			{
				
				if(changeCurrentQuadrant){
					changeQuadrant(aimQuadrant);
				}
				getNextPosition();
				turns++;
				contagem++;
				//para a futura troca de quadrante
				// if(changeCurrentQuadrant == true) //caso do populado e outros que podem "esperar', mudanças por eventos sao imediatas
				// getnextPosition para a borda mais proxima do proximo quadrante de interesse	
			}
			GoTo(nextX,nextY);
			execute();
			
			
		}
	}
	

	void scanLimits(){
		radarAng = getRadarHeading();
		

		if(limitRRadar == 90 && limitLRadar == 270){
			if(radarAng >=limitRRadar-1 && radarAng < limitLRadar-1){
			//inverte o sentido
			
			radarSentido = -1;
			}
		    else if(radarAng >=limitLRadar-1){
			radarSentido  =1;
			} 
		}
		else if(radarAng >=limitRRadar-1){
			//inverte o sentido
			
			radarSentido = -1;
		} 
		//quando chegar no limite do trilho (0), volta a subir de forma crescente ->(1) ->(2)
		else if(radarAng <= limitLRadar+1)
		{
			//sentido volta ao normal
			radarSentido=1;
		}else{
			//turnRadarRight(180);
		
		}
	
		turnGunRight(20*radarSentido);
		execute();
	}

	void getNextPosition(){
			//fazer idNextTrack começarr como o mais proximo, nao zero
		nextX = trackList[borderLV][idCurrentQuad][idNextTrack][0];
		//nextX = trackList[borderLW][idNextTrack][0]
		nextY = trackList[borderLV][idCurrentQuad][idNextTrack][1];
		int currentSentido = sentido;
		idCurrentTrack = idNextTrack;
		// ja esta no x e y do ponto
		/*
		if(changeCurrentQuadrant == true){
			//calcular se os nexts são mais proximos do ponto de objetivo, se nao for chegou na borda maxima do qquadrante intermediario
			// passa para o proximo quadrante (chegando no quadrante de objetivo)
			if(Math.abs(nextX -aimX) <= Math.abs(getX() -aimX) && Math.abs(nextY -aimY) <= Math.abs(getY() -aimY)){
			//o next aproxima do objetivo, entao continua o movimento
			

			}
			else{
			//chegou na borda do quadrante e precisa trocar para ir para o objetivo
			}
			
		}*/
		
		//quando chegar no ultimo ponto do trilho (2), volta de forma decrescente ->(1) ->(0)
		if(idNextTrack >=2){
			//inverte o sentido
			sentido = -1;
		} 
		//quando chegar no limite do trilho (0), volta a subir de forma crescente ->(1) ->(2)
		else if(idNextTrack <= 0)
		{
			//sentido volta ao normal
			sentido=1;
		}
	
		idNextTrack += sentido;
		//redefinir o angulo da arma se ja nao estiver mirando em um inimigo
		
		
	}
	
	void GoTo(double NextX, double NextY)
	{
		//caso use advanced considerar essas variaveis de controle
		//Arrived = false;
		waitRotation = true;
		
		double moveDistance = (Math.max(robotX, nextX) - Math.min(robotX, nextX) > Math.max(robotY, nextY) - Math.min(robotY, nextY)) ? Math.max(robotX, nextX) - Math.min(robotX, nextX) : Math.max(robotY, nextY) - Math.min(robotY, nextY);
	
		///metodo para calcular e virar para o angulo apenas
		turnAngleToXAndY(NextX, NextY);
		if(!waitRotation){ 
			setAhead(moveDistance);
		}
	}
	
	void turnAngleToXAndY(double NextX, double NextY){
		
		double currentHeading = getHeading();
		double angle = 57.296 * (normalAbsoluteAngle(Math.atan2(NextX- getX(), NextY - getY())));
		double diff = Math.round(currentHeading - angle);
	
		if(diff >0){
			
			if(diff <= 180)
			{
				setTurnLeft(diff);
			}
			else if(diff > 180)
			{
				setTurnRight(360 - currentHeading + angle);
			}
		}
		else if(diff < 0){
			
			if(diff < -180)
			{
				setTurnLeft(currentHeading + 360 - angle);
			}else{
				setTurnRight(Math.abs(diff));
			} 
		}
		else{
			//diff == 0
			waitRotation = false;
		} 

	}
	
	void decideTrack(){
		//ser o else final
		if(robotEnergy >50 && robotsLeft > 4 && !recentCollision){
			//ir para o mais populoso / quadrant almejado
			//talvex nao chamar aqui
			changeQuadrant(aimQuadrant);
		}
		
		if(recentCollision){
			//afastamento ja vai ser chamado
			// change border level
			//seguir para outra borda
			//se ainda tiver muitos robos, se afasta e volta para a borda principal 
		}
	//	if( movementExis == exisOfEnemy){
			//mudar de camada
			//verificar angulo oposto ao movimento do robo, usar mod 360 para normalizar
	}
		
	
	
	void changeQuadrant(int quad){
		


		robotX = getX();
		robotY = getY();

		//definir se a posiçao atual é mais proxima do proximo ponto do quadrante ou nao
		//se for mais distante verifica se o proximo (aim) quadrante
		int nearestId = 0;



		//cacl nearest do Aim
		if(Math.abs(robotX - trackList[borderLV][aimQuadrant][nearestId][0]) >= Math.abs(robotX - trackList[borderLV][aimQuadrant][nearestId +1][0]) && Math.abs(robotY - trackList[borderLV][aimQuadrant][nearestId][1]) >= Math.abs(robotY - trackList[borderLV][aimQuadrant][nearestId +1][1]))
		{
			nearestId++;
			System.out.println("11$$$$$$$$$$$$$$$$$$$$$$$$nearest: "+nearestId);
			if(Math.abs(robotX - trackList[borderLV][aimQuadrant][nearestId][0]) >= Math.abs(robotX - trackList[borderLV][aimQuadrant][nearestId +1][0]) && Math.abs(robotY - trackList[borderLV][aimQuadrant][nearestId][1]) >= Math.abs(robotY - trackList[borderLV][aimQuadrant][nearestId +1][1]))
			{
				nearestId++;
				System.out.println("222$$$$$$$$$$$$$$$$$$$$$$$$nearest: "+nearestId);
			
			}
			
		}
		else{
			if(Math.abs(robotX - trackList[borderLV][aimQuadrant][nearestId][0]) >= Math.abs(robotX - trackList[borderLV][aimQuadrant][nearestId +2][0]) && Math.abs(robotY - trackList[borderLV][aimQuadrant][nearestId][1]) >= Math.abs(robotY - trackList[borderLV][aimQuadrant][nearestId +2][1]))
			{
				nearestId += 2;
				System.out.println("333$$$$$$$$$$$$$$$$$$$$$$$$nearest: "+nearestId);
			
			}
		}
		//nearest definido
		System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$nearest: "+nearestId);
	//	verificar se o proximo track fica mais proximo do nearest do aim quadrant
		int nearestTrack =0;
		for(int i =1; i<3;i++){
			if(Math.abs(trackList[borderLV][idCurrentQuad][nearestTrack][0] - trackList[borderLV][aimQuadrant][nearestId][0]) >= Math.abs(trackList[borderLV][idCurrentQuad][i][1] - trackList[borderLV][aimQuadrant][nearestId][1]) ){
				nearestTrack++;
			}
				System.out.println("forr$$$$$$$$$$$$$$$$$$$$$$$$nearestTrack: "+nearestTrack);
		}
	

		//precisa do nearest do quad atual em relaçao ao nearest aim
		if(nearestTrack == idCurrentTrack && idCurrentQuad != aimQuadrant){
			//mudar para o proximo quadrante
			if(nearestTrack == 0){
				//testar o 2 do quadrante -1
				idNextTrack = 2;
				if(idCurrentQuad == 0){
					idCurrentQuad = 3;
				}else{
					idCurrentQuad--;
				}
				
			}
			else{
				//testar o 0 do quadrante +1
				idNextTrack = 0;
				if(idCurrentQuad == 3){
					idCurrentQuad = 0;
				}else{
					idCurrentQuad++;
				}
			}
		}
		else{
			//desativar o change
		//	changeCurrentQuadrant = false;
		}
		
		if(idCurrentQuad == aimQuadrant){
			changeCurrentQuadrant = false;
			idNextTrack = nearestId;
			System.out.println("0000000000000000000$$$$$$$$$$$$$$$$$$$$$$$$nearest: "+nearestId);
		}
		
		
	}
	
	void calcBorderExis(){
		
		
		double distanceWestWall, distanceEastWall, distanceSouthWall, distanceNorthWall;
			
		distanceWestWall = getX();
		distanceEastWall = getBattleFieldWidth() - getX();
		distanceSouthWall = getY();
		distanceNorthWall = getBattleFieldHeight() - getY();
		double nearest =Math.min(distanceWestWall, Math.min(distanceEastWall, Math.min(distanceSouthWall, distanceNorthWall)));
	
		if(distanceWestWall == nearest || distanceEastWall == nearest){
			//horizontal
			if(distanceEastWall == nearest){
				limitRRadar = 359;
				limitLRadar = 180;
			}
			else{
				limitRRadar = 180;
				limitLRadar = 1;
			}
			isOnHorizontalBorder =true;
			isOnVerticalBorder =false;
		}
		else{
			//vertical
			if(distanceNorthWall == nearest){
				limitRRadar = 270;
				limitLRadar = 90;
			}
			else{
				limitRRadar = 90;
				limitLRadar = 270;
			}
			isOnHorizontalBorder =false;
			isOnVerticalBorder =true;
		}
	}

	public void onScannedRobot(ScannedRobotEvent e) {
		
	//	if(reconhecimento)
		if(scanQuadrants){
			scanOpponentQuadrant(e);
		}
		else{
		
			scanOpponentQuadrant(e);
			double distancia = e.getDistance();
			String name = e.getName();
		

			if(distancia < 300 || (name.equals(nearestEnemy) && distancia < 400)){
				targetingEnemy = true;
		
			//parar a rotaçao e focar no enemy
			
				double absoluteBearing = getHeading() + e.getBearing();
				double bearingFromGun = normalRelativeAngleDegrees(absoluteBearing - getGunHeading());

				if (Math.abs(bearingFromGun) <= 3) {
					turnGunRight(bearingFromGun);
					Fire(distancia);
					execute();
				
				} else {
		
					turnGunRight(bearingFromGun);
					execute();
				}
				
				if (bearingFromGun == 0) {
					scan();
				}
			
	
		

			}

		
			else{
			//continua o movimento mas guarda a informaçao do mais proximo
			//vai completar a rotaçao e ter o mais proximo guardado entao eh a hora de atirar
			//targetingEnemy = false;
				if(distancia <= nearestEnemyDistance){
					nearestEnemyDistance = distancia;
					nearestEnemy = name;
				}
			}

			gunAng = getGunHeading();
			double enemyAngle = robotAng + e.getBearing();
			System.out.println("bbbbb$$$$$$$$$$$$$$$$$$$$$$$$: "+ e.getBearing());
			System.out.println("annnn$$$$$$$$$$$$$$$$$$$$$$$$: "+ enemyAngle);
		}

	}
	

	void Fire(double distancia){
			if(distancia < 100){
			setFire(3); //16
		}
		else if( distancia < 200)
		{
			if(fireConfidence >= 3){
				setFire(3); //16
			}
			else{
				setFire(2); //10
			}
		}
		else if(distancia <300)
		{
			if(fireConfidence >= 3){
				setFire(2); //10
			}
			else{
				setFire(1); //4
			}
		}
		else if( (isOnHorizontalBorder && distancia < getBattleFieldWidth()/2) || (isOnVerticalBorder && distancia < getBattleFieldHeight()/2) )
		{
			if(fireConfidence >= 3){
			setFire(2); //10
			}
			else if(fireConfidence >=2){
				setFire(1.5);
			}
			else{
				setFire(1);
			}
		
		}
		else{
			if(missedBullets <2){
				if(fireConfidence >= 3){
					setFire(1.5); 
				}
				else if(fireConfidence >= 2){
					setFire(1); //4
				}
				else{
					setFire(0.5); 
				}
			}
		}
	}
	
	public void onBulletHit(BulletHitEvent e)
	{
		
		String name = e.getName();
		if(lastFiredName.equals(name))
		{
			fireConfidence++;
		}
		else{
			lastFiredName = name;
			fireConfidence = 1; //nao errou mas nao tem a mesma confiança por ser inimigo diferente
		}
		missedBullets = 0;
	}
	
	public void onBulletMissed(BulletMissedEvent e) {
		fireConfidence = 0;
		missedBullets += 1;
	}
		
	public void onHitRobot(HitRobotEvent e) {
		System.out.println("angulo:" + e.getBearing());

    	// Se fomos nós que batemos no inimigo
    	if (e.isMyFault()) {

        	// Alinha canhão para frente do robô
        	setTurnGunRight(Utils.normalRelativeAngleDegrees(getHeading() - getGunHeading()));
        
			// Anda para trás
        	setBack(100);

        	// Executa tudo simultaneamente
        	execute();
    	}

    // Se o outro robô bateu em nós
    	else {

    	}
	}

	/**
	 * onHitByBullet: What to do when you're hit by a bullet
	 */
	public void onHitByBullet(HitByBulletEvent e) {
		// Replace the next line with any behavior you would like
		setBack(10);
	}
	
	public void onHitWall(HitWallEvent e) {
		turnLeft(180);
		System.out.println(e.getBearing());
	}
	
	void setTrackList()
	{
		//borda principal
		
		//quad 4
		
		trackList[0][0][0][0]= fieldWidth/2;//8
		trackList[0][0][0][1] = DR;//8
		trackList[0][0][1][0] = DR; //1
		trackList[0][0][1][1] = DR; //1
		trackList[0][0][2][0]= fieldWidth/2;//8
		trackList[0][0][2][1] = DR;//8
		trackList[0][0][2][0] = DR; //2
		trackList[0][0][2][1] = fieldHeight/2; //2
		

		//quad 1
		trackList[0][1][0][0] = DR; //2
		trackList[0][1][0][1] = fieldHeight/2; //2
		trackList[0][1][1][0] = DR;//3
		trackList[0][1][1][1] = fieldHeight - DR;//3
		trackList[0][1][2][0] = fieldWidth/2;//4
		trackList[0][1][2][1]= fieldHeight - DR;//4
		
		//quad 2
		trackList[0][2][0][0] = fieldWidth/2;//4
		trackList[0][2][0][1]= fieldHeight - DR;//4
		trackList[0][2][1][0] = fieldWidth - DR;//5
		trackList[0][2][1][1] = fieldHeight - DR;//5
		trackList[0][2][2][0] = fieldWidth - DR;//6
		trackList[0][2][2][1] = fieldHeight/2;//6
		

		//quad 3
		trackList[0][3][0][0] = fieldWidth - DR;//6
		trackList[0][3][0][1] = fieldHeight/2;//6
		trackList[0][3][1][0] = fieldWidth - DR;//7
		trackList[0][3][1][1] = DR;//7
		trackList[0][3][2][0] = fieldWidth/2;//8
		trackList[0][3][2][1] = DR;//8
		


		//borda manto
		/*
		trackList[1][0][0] = DR +(fieldWidth/2 - DR)/3;
		trackList[1][0][1] = DR + (fieldHeight/2 - DR)/3;
		trackList[1][1][0] = DR +(fieldWidth/2 - DR)/3;
		trackList[1][1][1] = fieldHeight/2;
		trackList[1][2][0] = DR +(fieldWidth/2 - DR)/3;
		trackList[1][2][1] = fieldHeight - DR -(fieldHeight/2 -DR)/3;
		trackList[1][3][0] = fieldWidth/2;
		trackList[1][3][1] = fieldHeight - DR -(fieldHeight/2 -DR)/3;
		trackList[1][4][0] = fieldWidth - DR - (fieldWidth/2 - DR)/3;
		trackList[1][4][1] = fieldHeight - DR -(fieldHeight/2 -DR)/3;
		trackList[1][5][0] = fieldWidth - DR - (fieldWidth/2 - DR)/3;
		trackList[1][5][1] = fieldHeight/2;
		trackList[1][6][0] = fieldWidth - DR - (fieldWidth/2 - DR)/3;
		trackList[1][6][1] = DR + (fieldHeight/2 - DR)/3;
		trackList[1][7][0] = fieldWidth/2;
		trackList[1][7][1] = DR + (fieldHeight/2 - DR)/3;
		*/

		//borda núcleo?????? apenas se o mapa for maior doq 1000
	}
}