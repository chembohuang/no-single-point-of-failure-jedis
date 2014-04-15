/**
 * 
 */
package com.csair.wx.cache.redis;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;



/**
 * @author chembo Mar 13, 2014
 */
@ContextConfiguration(locations = { "classpath:jedis-config-test.xml" })
public class JedisTest extends
        AbstractJUnit4SpringContextTests {
    
    @Autowired
    public FailoverJedisPool jedisPool;
    
    @Test
    public void testJedis(){
        jedisPool.getJedis().saveOrUpdate("testKey", "testValue");
        Assert.assertEquals(jedisPool.getJedis().getValue("testKey",String.class),"testValue");
    }
    
}
