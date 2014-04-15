/** 
 * @Title:TODO  
 * @Desription:TODO
 * @Company:CSN
 * @ClassName:FailoverJedisIntercepter.java
 * @Author:chembo
 * @CreateDate:2014年3月7日   
 * @UpdateUser:chembo  
 * @Version:0.1 
 *    
 */

package com.csair.wx.cache.redis;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.apache.log4j.Logger;

import redis.clients.jedis.JedisShardInfo;

import com.csair.wx.cache.redis.util.SpringContextutil;

/**
 * @ClassName: FailoverJedisIntercepter
 * @Description: TODO
 * @author: chembo
 * @date: 2014年3月7日
 * 
 */
public class FailoverJedisIntercepter implements MethodInterceptor {
    
    private static final Logger logger = Logger
            .getLogger(FailoverJedisIntercepter.class);
    private JedisFacade master;
    private final FailoverJedisCluster cluster;
    private final FailoverJedisPool pool;
    
    public FailoverJedisIntercepter(FailoverJedisCluster cluster) {
        this.cluster = cluster;
        this.pool = (FailoverJedisPool) SpringContextutil
                .getBean("jedisPool");
        initMaster();
    }
    
    private void initMaster(){
        /*while(master == null){
            JedisShardInfo info = cluster.getMaster();
            if (info != null) {
                JedisFacade jedis = new JedisFacadeImpl(info);
                //System.out.println("init master for "+info +" jedis "+jedis);
                if (FailoverJedisCluster.isHealthy(jedis)) {
                    master = jedis;
                    //System.out.println("source:" + jedis.getIdentity() + "  " + jedis.toString());
                } else {
                    master = null;
                    System.out
                            .println("source is sick. goto elect new master.");
                    cluster.electNewMaster();
                }
            }
        }*/
        JedisShardInfo info = cluster.getMaster();
        if (info != null) {
            master = new JedisFacadeImpl(info);
        }
    }
    /**
     * @param obj
     * @param method
     * @param args
     * @param proxy
     * @return
     * @throws Throwable
     * @see net.sf.cglib.proxy.MethodInterceptor#intercept(java.lang.Object,
     *      java.lang.reflect.Method, java.lang.Object[],
     *      net.sf.cglib.proxy.MethodProxy)
     */
    @Override
    public Object intercept(Object object, Method method, Object[] args,
            MethodProxy methodProxy) throws Throwable {
        if (method.getName().equals("getIdentity")
                || method.getName().equals("hashCode")
                || method.getName().equals("equals")
                || method.getName().equals("toString")
                || method.getName().equals("ping")
                /*|| method.getName().equals("isConnected")
                || method.getName().equals("disconnect")
                || method.getName().equals("quit")*/
                ) {
            try {
                return method.invoke(master, args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Object result = null;
        int i = 0;
        //two chances to invoke the method, if fail in second time, throws exception.
        while (result == null && i < 2) {
            i++;
            try {
                result = method.invoke(master, args);
                if (i == 1) {// cause the proxy of the second time isn't from the pool, do not need to return. 
                    try {
                        pool.returnResourceObject(master);
                    } catch (Exception e) {
                        e.printStackTrace();
                        pool.returnBrokenResource(master);
                    }
                }
            } catch (InvocationTargetException ex) {
                logger.error(ex.getMessage(), ex);
                logger.info("invoke redis error, now reselect new master and try again and i="+i);
                if (i == 2) {
                    throw ex;
                }
                pool.returnBrokenResource(master);
                master = null;
                cluster.electNewMaster();
                initMaster();
            }
        }
        return result;
    }
    /*public void destroyProxy() {
        try {
            if(master != null){
                master.quit();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public boolean validateProxy() {
        if(master == null){
            return false;
        }
        try {
            if (FailoverJedisCluster.isHealthy(master)){
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        return false;
    }*/
}
