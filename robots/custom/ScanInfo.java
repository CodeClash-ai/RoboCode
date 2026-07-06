// CodeClash ladder import
// Source: https://github.com/UR4N0-235/UR4NO/blob/HEAD/UR4NO.java
// Author: UR4N0-235   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

public class ScanInfo {
public double x = 0, y = 0, d = 0;
public long t = 0;
public double v = 0;
public double acc = 0;
public double atm = 0;
public double dtm = 0;
public double dtwf = 0;
public double dtwb = 0;
public double runTime = 0;
public double lastRunTime = 0;
public double myGunHeat = 0;
public boolean fired = false;
public ScanInfo previous;
public ScanInfo next;

public ScanInfo (double x1, double y1, double d1, double v1, long t1) {
        x = x1;
        y = y1;
        d = d1;
        v = v1;
        t = t1;
}
}
