no-single-poing-of-failure jedis
=========

A java redis failover solution based on java-code. Switch automatically when the master is down.
* Use common-pool2 as the jedis-pool.
* Use the jdk to  Serialize and deserialize an object, you may want to use Fast-json or Kryo or whatever you like instead, just change the code in JedisFacadeImpl.java.
* Base on jedis 2.4.1 (the latest version)
* Redis servers are master-slave relationship, one master and multiple slaves. But you don't need to configure this relationship by using the command ```slave of ``` in the ```redis.conf``` file , the code will do the job.
* Do not cache the JedisFacade instance. Whenever you want it, you call it from the jedisPool directly, like ```jedisPool.getJedis()```, because the instance that you borrow from the pool need to return to the pool after you finish your call. And the return job will be done by the code automatically, since we proxied the FedisFacade with the cglib.

Usage
----
0.pom.xml adds dependences:
```xml
<dependency>  
        <groupId>redis.clients</groupId>  
        <artifactId>jedis</artifactId>  
        <version>2.4.1</version>  
    </dependency>  
    <dependency>  
      <groupId>org.ow2.asm</groupId>  
      <artifactId>asm-util</artifactId>  
      <version>4.0</version>  
    </dependency>  
    <dependency>  
        <groupId>cglib</groupId>  
        <artifactId>cglib</artifactId>  
        <version>3.0</version>  
    </dependency>  
```

1.The following description is a way to use FailoverJedisPool within the Spring container. If you are not using the spring, you can always use the 'new way' to instantiate a FailoverJedisPool object as long as you can pass the FailoverJedisPool instance to the FailoverJedisIntercepter.

```xml
<!-- jedis configuration starts-->
<bean id="config" class="org.apache.commons.pool2.impl.GenericObjectPoolConfig">
         <property name="maxTotal" value="200"></property>  
        <property name="maxIdle" value="50"></property> 
        <property name="minIdle" value="10"></property> 
        <property name="maxWaitMillis" value="15000"></property>  
        <property name="lifo" value="true"></property>
        <property name="blockWhenExhausted" value="true"></property>
        <property name="testOnBorrow" value="false"></property>
        <property name="testOnReturn" value="false"></property>
        <property name="testWhileIdle" value="false"></property>
        <property name="timeBetweenEvictionRunsMillis" value="30000"></property>
 	</bean>
 	
    <bean id="jedisCluster" class="com.csair.wx.cache.redis.FailoverJedisCluster" init-method="init">
    	<property name="redisServers"  value="10.92.2.61:6379,10.92.2.60:6379" />
    </bean>

    <bean id="jedisPool" class="com.csair.wx.cache.redis.FailoverJedisPool">
    	<constructor-arg type="org.apache.commons.pool2.impl.GenericObjectPoolConfig" ref="config" />
    	<constructor-arg type="com.csair.wx.cache.redis.FailoverJedisCluster" ref="jedisCluster" />
    	<constructor-arg type="org.apache.commons.pool2.impl.AbandonedConfig" ref="abandonConfig" />
    </bean>
    
    <bean id="abandonConfig" class="org.apache.commons.pool2.impl.AbandonedConfig">
		<property name="removeAbandonedTimeout" value="10"></property>
		<property name="removeAbandonedOnBorrow" value="true"></property>
		<property name="removeAbandonedOnMaintenance" value="true"></property>
	</bean>
	
    <bean class="com.csair.wx.cache.redis.util.SpringContextutil" />
<!-- jedis configuration ends-->
```

2.Inject your bean:
```java
    @Autowired
	private FailoverJedisPool jedisPool;
    
```

3.Set an object into the memory:
```java
    jedisPool.getJedis().saveOrUpdate("myKey", someSerializeObject);
```
or set it with an expired seconds:
```java
    jedisPool.getJedis().saveOrUpdate("myKey", someSerializeObject,60*30);
```

4.Get it from the memory:
```java
    SomeSerializeObject g = jedisPool.getJedis().getValue("myKey", SomeSerializeObject.class);
```
