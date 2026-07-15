#!/bin/bash
set -e

# Compile MyTank
echo "Compiling..."
javac -cp libs/robocode.jar robots/custom/MyTank.java

# Run battle against sample.Corners by packaging sample.Corners correctly
# Let's see if sample robots are in libs/robocode.jar or if we need to set classpath.
# Actually we can run a battle against custom.MyTank vs custom.MyTank (ourselves) to see if it runs fine.
