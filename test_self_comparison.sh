#!/bin/bash
# Prepare battles config
cat <<'BATTLE' > battles/test_comparison.battle
#Battle Properties
robocode.battleField.width=800
robocode.battleField.height=600
robocode.battle.numRounds=30
robocode.battle.gunCoolingRate=0.1
robocode.battle.rules.inactivityTime=450
robocode.battle.hideEnemyNames=false
robocode.battle.selectedRobots=custom.MyTank*,custom_old.MyTank*
BATTLE

# Run the battle
java -cp libs/robocode.jar robocode.Robocode -battle battles/test_comparison.battle -results test_results_comparison.txt -nodisplay

cat test_results_comparison.txt
