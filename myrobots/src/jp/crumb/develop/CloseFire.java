package jp.crumb.develop;

import boss.*;
import jp.crumb.utils.MoveType;
import jp.crumb.utils.Util;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */





/**
 *
 * @author crumb
 */
public class CloseFire extends Boss {

    @Override
    protected void cbFirst() {
        super.cbFirst();
        setFireMode(ctx.MODE_FIRE_MANUAL);
    }

}
