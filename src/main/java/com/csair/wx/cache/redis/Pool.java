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

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;

import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

public abstract class Pool<T> {
    GenericObjectPool<T> internalPool; 
    private static final Logger logger = Logger
            .getLogger(Pool.class);
    
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
        //setHeartbeat(10);
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

    private final Object monitor = new Object();
    private ScheduledFuture<?> future;
    private ScheduledExecutorService executor;
    private final class HealthChecker implements Runnable {
        public void run() {
            try {
            	logger.info("----------pool status----------");
            	logger.info("getNumActive::"+internalPool.getNumActive());
            	logger.info("getNumIdle::"+internalPool.getNumIdle());
            	logger.info("getNumWaiters::"+internalPool.getNumWaiters());
            	logger.info("getBorrowedCount::"+internalPool.getBorrowedCount());
            	logger.info("getCreatedCount::"+internalPool.getCreatedCount());
            	logger.info("getDestroyedByEvictorCount::"+internalPool.getDestroyedByEvictorCount());
            	logger.info("getDestroyedCount::"+internalPool.getDestroyedCount());
            	logger.info("----------pool status----------");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private final void setHeartbeat(int heartbeatSeconds) {
        synchronized (this.monitor) {
            if (future == null && heartbeatSeconds > 0) {
                long interval = SECONDS.toNanos(heartbeatSeconds);
                executor = createExecutorIfNecessary();
                Runnable task = new HealthChecker();
                this.future = executor.scheduleAtFixedRate(task, interval,
                        interval, TimeUnit.NANOSECONDS);
            }
        }
    }
    
    private final ScheduledExecutorService createExecutorIfNecessary() {
        synchronized (this.monitor) {
            if (this.executor == null) {
                this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("pool-checker");
                        return t;
                    }
                });
            }
            return this.executor;
        }
    }
}
