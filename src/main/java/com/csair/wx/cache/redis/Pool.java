/** 
 * @Title:TODO  
 * @Desription:TODO
 * @Company:CSN
 * @ClassName:Pool.java
 * @Author:chembo
 * @CreateDate:2014年3月10日   
 * @UpdateUser:chembo  
 * @Version:0.1 
 *    
 */

package com.csair.wx.cache.redis;

/** 
 * @ClassName: Pool 
 * @Description: TODO 
 * @author: chembo 
 * @date: 2014年3月10日
 * 
 */

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

public abstract class Pool<T> {
    protected GenericObjectPool<T> internalPool;
    
    /**
     * Using this constructor means you have to set and initialize the
     * internalPool yourself.
     */
    public Pool() {
    }
    
    public Pool(final GenericObjectPoolConfig poolConfig,
            PooledObjectFactory<T> factory, AbandonedConfig abandonedConfig) {
        initPool(poolConfig, factory,abandonedConfig);
    }
    
    public void initPool(GenericObjectPoolConfig poolConfig,
            PooledObjectFactory<T> factory, AbandonedConfig abandonedConfig) {
        if (this.internalPool != null) {
            try {
                closeInternalPool();
            } catch (Exception e) {
                
            }
        }
        if(abandonedConfig == null){
            abandonedConfig = new AbandonedConfig();
            abandonedConfig.setRemoveAbandonedOnBorrow(true);
            abandonedConfig.setRemoveAbandonedTimeout(20);
            abandonedConfig.setRemoveAbandonedOnMaintenance(true);
        }
        if(poolConfig == null){
            poolConfig = new GenericObjectPoolConfig();
            poolConfig.setMaxTotal(200);
            poolConfig.setMaxIdle(50);
            poolConfig.setMinIdle(10);
            poolConfig.setMaxWaitMillis(15000);
            poolConfig.setLifo(true);
            poolConfig.setBlockWhenExhausted(true);
            poolConfig.setTestOnBorrow(false);
            poolConfig.setTestOnReturn(false);
            poolConfig.setTestWhileIdle(false);
            poolConfig.setTimeBetweenEvictionRunsMillis(30000);
        }
        this.internalPool = new GenericObjectPool<T>(factory, poolConfig,abandonedConfig);
    }
    
    public Pool(final GenericObjectPoolConfig poolConfig,
            PooledObjectFactory<T> factory) {
        initPool(poolConfig, factory,null);
    }
    
    public T getResource() {
        try {
            return internalPool.borrowObject();
        } catch (Exception e) {
            throw new JedisConnectionException(
                    "Could not get a resource from the pool", e);
        }
    }
    
    public void returnResourceObject(final T resource) {
        try {
            internalPool.returnObject(resource);
        } catch (Exception e) {
            throw new JedisException(
                    "Could not return the resource to the pool", e);
        }
    }
    
    public void returnBrokenResource(final T resource) {
        returnBrokenResourceObject(resource);
    }
    
    public void returnResource(final T resource) {
        returnResourceObject(resource);
    }
    
    public void destroy() {
        closeInternalPool();
    }
    
    protected void returnBrokenResourceObject(final T resource) {
        try {
            internalPool.invalidateObject(resource);
        } catch (Exception e) {
            throw new JedisException(
                    "Could not return the resource to the pool", e);
        }
    }
    
    protected void closeInternalPool() {
        try {
            internalPool.close();
        } catch (Exception e) {
            throw new JedisException("Could not destroy the pool", e);
        }
    }
}
