package com.csair.wx.cache.redis;

import net.sf.cglib.proxy.Enhancer;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.log4j.Logger;

/**
 * 
* @ClassName: FailoverJedisFactory 
* @Description: TODO 
* @author: chembo 
* @date: 2014年3月26日
*
 */
public class FailoverJedisFactory extends BasePooledObjectFactory<JedisFacade> {

    private static final Logger logger = Logger
            .getLogger(FailoverJedisFactory.class);
    private final FailoverJedisCluster cluster;
    
    public FailoverJedisFactory(FailoverJedisCluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public void destroyObject(PooledObject<JedisFacade> p) throws Exception {
        final JedisFacade jedis = p.getObject();
        if (jedis.isConnected()) {
            try {
                try {
                    jedis.quit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                jedis.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    @Override
    public boolean validateObject(PooledObject<JedisFacade> p) {
        final JedisFacade jedis = p.getObject();
        try {
            return  FailoverJedisCluster.isHealthy(jedis);
        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
    }

    @Override
    public JedisFacade create() throws Exception {
        FailoverJedisIntercepter handler = new FailoverJedisIntercepter(cluster);
        Enhancer enhancer = new Enhancer();
        enhancer.setCallback(handler);
        enhancer.setInterfaces(new Class[] { JedisFacade.class });
        JedisFacade proxy = (JedisFacade) enhancer.create();
        return proxy;
    }

    @Override
    public PooledObject<JedisFacade> wrap(JedisFacade obj) {
        return new DefaultPooledObject<JedisFacade>(obj);
    }

}