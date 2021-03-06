/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.crumb.develop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jp.crumb.CrumbContext;
import jp.crumb.CrumbRobot;
import jp.crumb.adv.AdbCrumbContext;
import jp.crumb.adv.AdvCrumbRobot;
import jp.crumb.utils.Enemy;
import jp.crumb.utils.MoveType;
import jp.crumb.utils.RobotPoint;


/**
 *
 * @author crumb
 */
public class Inertia extends AdvCrumbRobot<AdbCrumbContext> {
    @Override
    protected void cbThinking() {
        for ( Map.Entry<String,Enemy> e : ctx.nextEnemyMap.entrySet() ) {
            ctx.setLockonTarget(e.getValue().name);
        }
        setRadarMode(ctx.MODE_RADAR_LOCKON);
        setGunMode(ctx.MODE_GUN_LOCKON);
        setMoveMode(ctx.MODE_MOVE_LOCKON1);
        setFireMode(ctx.MODE_FIRE_AUTO);
    }

    @Override
    protected List<MoveType> initialAimTypeList() {
        List<MoveType> moveTypeList = new ArrayList<>(2);
        MoveType moveType = new MoveType(MoveType.TYPE_INERTIA);
        moveType.score = 0.001; // Initial type (will be overrided by first hit!!)
        moveTypeList.add(moveType);
        return moveTypeList;
    }

    @Override
    protected List<MoveType> initialShotTypeList() {
       List<MoveType> moveTypeList = new ArrayList<>(2);
       MoveType moveType = new MoveType(MoveType.TYPE_INERTIA);
       moveTypeList.add(moveType);
       return moveTypeList;
     }

}
