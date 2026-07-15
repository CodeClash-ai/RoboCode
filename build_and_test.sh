#!/bin/bash
mkdir -p robots/custom
javac -cp libs/robocode.jar robots/custom/MyTank.java
./test_versus_opponent.sh
