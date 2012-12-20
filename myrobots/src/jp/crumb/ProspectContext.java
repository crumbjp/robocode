/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.crumb;

import jp.crumb.utils.RobotPoint;

/**
 *
 * @author crumb
 */
public class ProspectContext {
    public int round = 0;
    public long   time   = 0;
    public long   shotTime   = 0;
    public String shotTarget = null;
    public double diff = 0;
    public int    towards    = 0;
    public RobotPoint  baseTarget = null;
    public RobotPoint  baseLog    = null;

}