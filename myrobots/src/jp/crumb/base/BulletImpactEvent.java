/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.crumb.base;

import java.io.Serializable;

/**
 *
 * @author crumb
 */
public class BulletImpactEvent implements Serializable{
    public String key;

    public BulletImpactEvent(String key) {
        this.key = key;
    }
}
