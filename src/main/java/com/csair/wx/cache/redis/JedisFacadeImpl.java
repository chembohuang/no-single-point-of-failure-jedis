package com.csair.wx.cache.redis;

import java.net.URI;

import org.springframework.util.SerializationUtils;

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
    
    /*private SerializerFeature[] featureArr = { SerializerFeature.WriteClassName};*/

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
    public String saveOrUpdate(String key, Object object) {
        return this.saveOrUpdate(key, object, DEFAULT_EXPIRED_SECOND);
    }
    
    @Override
    public String saveOrUpdate(String key, Object object, int expiredSeconds) {
        return this.setex(key.getBytes(),expiredSeconds, SerializationUtils.serialize(object));
    }
    
    @Override
    public <T> T getValue(String key, Class<T> type) {
        return (T) SerializationUtils.deserialize(this.get(key.getBytes()));
    }
}