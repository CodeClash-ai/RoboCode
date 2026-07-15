#!/bin/bash
./robocode.sh -battle battles/test_vs_opponent.battle -results test_results_opponent.txt -nodisplay
cat test_results_opponent.txt
