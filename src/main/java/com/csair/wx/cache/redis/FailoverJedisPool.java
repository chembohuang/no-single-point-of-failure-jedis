/** 
 * @Title:TODO  
 * @Desription:TODO
 * @Company:CSN
 * @ClassName:FailSafeJedisPool.java
 * @Author:chembo
 * @CreateDate:2014年2月25日   
 * @UpdateUser:chembo  
 * @Version:0.1 
 *    
 */

package com.csair.wx.cache.redis;


import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;


/** 
 * @ClassName: FailSafeJedisPool 
 * @Description: TODO 
 * @author: chembo 
 * @date: 2014年2月25日
 * 
 */
public class FailoverJedisPool extends Pool<JedisFacade> {
    
    public FailoverJedisPool(final  GenericObjectPoolConfig poolConfig,
            FailoverJedisCluster cluster,AbandonedConfig abandonedConfig) {
        super(poolConfig, new FailoverJedisFactory(cluster),abandonedConfig);
    }
   
    /**
     * do not cache me. call it directly when you use it.
     * @return
     */
    public JedisFacade getJedis() {
        return getResource();
    }

   /* public void returnJedis(JedisFacade j) {
        returnResource(j);
    }
    
    public void returnBrokenJedis(JedisFacade j) {
        returnBrokenResource(j);
    }*/
} 