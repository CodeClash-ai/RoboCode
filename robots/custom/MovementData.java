// CodeClash ladder import
// Source: https://github.com/PEZ/Bots/blob/HEAD/pez/Marshmallow.java
// Author: PEZ (Peter Strömberg) et al.   License: RWPCL
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

import java.awt.geom.Point2D;

public class MovementData {
    public void setDestination(Point2D destination) {
        m_destination = destination;
    }
    
    public Point2D getDestination() {
        return m_destination;
    }
    
    private Point2D m_destination = null;    
}
