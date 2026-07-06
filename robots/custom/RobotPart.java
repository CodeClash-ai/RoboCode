// CodeClash ladder import
// Source: https://github.com/pranav-prakash/TheCarverBot/blob/HEAD/pt/TheCarver.java
// Author: Pranav Prakash   License: unspecified
// Imported verbatim; only repackaged to the arena package + main class renamed to MyTank (+ helper files flattened).
package custom;

/**
 * Robot part interface
 *
 * @author Pranav Prakash
 * @author Period: 7
 * @author Assignment: Robo05PartsBot
 * @author Sources
 * @version May 14, 2015
 */
public interface RobotPart {
    /**
     * Initialize
     */
    void init();


    /**
     * Move
     */
    void move();
}