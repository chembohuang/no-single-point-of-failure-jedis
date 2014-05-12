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
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

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
    	 if (method.getName().equals("finalize")){
         	return null;
         }
    	//filters the methods that doesn't need to be proxied.
    	//so don't call these methods through the proxy instance, like:  
    	if (method.getName().equals("getIdentity")
                || method.getName().equals("hashCode")
                || method.getName().equals("equals")
                || method.getName().equals("toString")
                || method.getName().equals("ping")
                || method.getName().equals("info")
                ) {
            try {
                return method.invoke(master, args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Object result = null;
        boolean doneInvoke = false;
        int i = 0;
        //two chances to invoke the method, if fails for the second time, throws the exception.
        while (!doneInvoke && i < 2) {
            i++;
            try {
            	result = method.invoke(master, args);
                if (i == 1) {// because the proxy of the second time isn't from the pool, do not need to return. 
                    try {
                        pool.returnResourceObject(master);
                    } catch (Exception e) {
                    	logger.error(e);
                        pool.returnBrokenResource(master);
                    }
                }
                doneInvoke = true;
            }  catch (InvocationTargetException ex) {
            	try{
            		throw ex.getTargetException();
            	}catch(JedisException e){
            		logger.error(e.getMessage(),e);
            		logger.error(Thread.currentThread().getId()+"  invoke redis error, i="+i);
                	if (i == 2) {
                        throw e;
                    }
                    pool.returnBrokenResource(master);
                    master = null;
                    cluster.electNewMaster();
                    initMaster();
            	}
            }
            catch(Exception e){
            	logger.error(e.getMessage(),e);
            }
        }
        return result;
    }
}
