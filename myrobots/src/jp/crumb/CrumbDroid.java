/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.crumb;

import robocode.Droid;

/**
 *
 * @author crumb
 */
public class CrumbDroid extends CrumbRobot<CrumbContext> implements Droid {


    public CrumbDroid() {
    }

    @Override
    protected void cbFirst() {
        super.cbFirst();
        GT_DIM = 1.5;
    }

    @Override
    protected void cbThinking() {
        if ( ! ctx.isGunMode(ctx.MODE_GUN_MANUAL )) {
            this.setGunMode(ctx.MODE_GUN_LOCKON);
        }
    }
    @Override
    protected void cbFiring() {
        if ( ctx.isFireMode(ctx.MODE_FIRE_AUTO) ) {
            firing(3,0);
        }
    }
    
    
}