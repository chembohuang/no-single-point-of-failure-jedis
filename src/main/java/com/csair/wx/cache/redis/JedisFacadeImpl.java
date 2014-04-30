package com.csair.wx.cache.redis;

import java.net.URI;

import org.springframework.util.SerializationUtils;

import com.alibaba.fastjson.JSON;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;

/**
 * 
 * @ClassName: JedisFacadeImpl
 * @Description: TODO
 * @author: chembo
 * @date: 2014年3月26日
 * 
 */
public class JedisFacadeImpl extends Jedis implements JedisFacade {
    
    private final long randomIdentity;
    
    private final static int DEFAULT_EXPIRED_SECOND = 60 * 30; // half an hour
    
    private String host;
    
    private int port;
    
    public JedisFacadeImpl(final String host) {
        super(host);
        randomIdentity = System.nanoTime();
        this.host = host;
    }
    
    public JedisFacadeImpl(final String host, final int port) {
        super(host, port);
        randomIdentity = System.nanoTime();
        this.host = host;
        this.port = port;
    }
    
    public JedisFacadeImpl(final String host, final int port, final int timeout) {
        super(host, port, timeout);
        randomIdentity = System.nanoTime();
        this.host = host;
        this.port = port;
    }
    
    public JedisFacadeImpl(JedisShardInfo shardInfo) {
        super(shardInfo);
        randomIdentity = System.nanoTime();
        this.host = shardInfo.getHost();
        this.port = shardInfo.getPort();
    }
    
    public JedisFacadeImpl(URI uri) {
        super(uri);
        randomIdentity = System.nanoTime();
    }
    
    @Override
    public String toString() {
        return "JedisFacadeImpl [ host="
                + host + ", port=" + port + ", randomIdentity=" +randomIdentity +"]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        else if (o instanceof JedisFacade) {
            JedisFacade j = (JedisFacade) o;
            return this.getIdentity() == j.getIdentity();
        }
        return false;
    }
    
    @Override
    public long getIdentity() {
        return randomIdentity;
    }
    
    @Override
    public Long saveOrUpdate(String key, Object object) {
        return this.saveOrUpdate(key, object, DEFAULT_EXPIRED_SECOND);
    }
    
    @Override
    public Long saveOrUpdate(String key, Object object, int expiredSeconds) {
        /*
         * this.set(key, JSON.toJSONString(object)); this.expire(key,
         * DEFAULT_EXPIRED_SECOND);
         */
        if (null != object) {
            /*this.set(key.getBytes(), SerializationUtils.serialize(object));
            return this.expire(key.getBytes(), expiredSeconds);*/
        	this.set(key, JSON.toJSONString(object));
        	return this.expire(key,expiredSeconds);
        }
        return null;
    }
    
    @Override
    public <T> T getValue(String key, Class<T> type) {
         return JSON.parseObject(this.get(key), type); 
        /*if(type.isInstance(String.class)){
            return (T)get(key);
        }
        return (T) SerializationUtils.deserialize(this.get(key.getBytes()));*/
    }
}