package com.csair.wx.cache.redis;

import redis.clients.jedis.AdvancedBinaryJedisCommands;
import redis.clients.jedis.AdvancedJedisCommands;
import redis.clients.jedis.BasicCommands;
import redis.clients.jedis.BinaryJedisCommands;
import redis.clients.jedis.BinaryScriptingCommands;
import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.MultiKeyBinaryCommands;
import redis.clients.jedis.MultiKeyCommands;
import redis.clients.jedis.ScriptingCommands;

/**
 * 
* @ClassName: JedisFacade 
* @Description: TODO 
* @author: chembo 
* @date: 2014年3月26日
*
 */
public interface JedisFacade extends BasicCommands, BinaryJedisCommands,
        MultiKeyBinaryCommands, AdvancedBinaryJedisCommands,
        BinaryScriptingCommands, JedisCommands, MultiKeyCommands,
        AdvancedJedisCommands, ScriptingCommands{
    
    //to distinguish JedisFacade instances proxied by CGLIB in the concurrentmap.
    long getIdentity();
    
    boolean isConnected();
    
    void disconnect();

    String saveOrUpdate(String key,Object object);
    
    <T> T getValue(String key,Class<T> type);
    
    String saveOrUpdate(String key,Object object,int expiredSeconds);
}