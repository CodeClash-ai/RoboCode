// CodeClash ladder import
// Source: https://github.com/gjgomez/RoboCodeSample/blob/HEAD/Mb2.java
// Author: gjgomez   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;
import robocode.*;
import java.util.*;
import robocode.util.*;

public interface IFiringImpl
{
	public void performFiringLogic(AdvancedRobot sourceRobot, TargetRobot targetRobot);
}
