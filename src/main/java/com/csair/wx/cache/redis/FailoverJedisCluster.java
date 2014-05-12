package com.csair.wx.cache.redis;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.exceptions.JedisException;

/**
 * 
* @ClassName: FailoverJedisCluster 
* @Description: TODO 
* @author: chembo 
* @date: 2014年3月26日
*
 */
public class FailoverJedisCluster {
    private static final Logger logger = Logger
            .getLogger(FailoverJedisCluster.class);

    private final int JEDIS_TIMEOUT = 10000;
    private final int HEART_BEAT_CHECKER_INTERVAL = 10;
    private final String HEALTH_CHECKER_THREAD_NAME = "health-checker-thread";
    private List<JedisShardInfo> healthMembers = new Vector<JedisShardInfo>();
    private List<JedisShardInfo> sickMembers = new Vector<JedisShardInfo>();
    private JedisShardInfo elected = null;
    private final Object monitor = new Object();
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> future;
    private final Object lock = new Object();
    private final ReentrantLock interRock = new ReentrantLock();
    private final Condition masterNotnull = interRock.newCondition();
    private String redisServers;
    
    private Map<JedisShardInfo,JedisFacade> jedisMap = new ConcurrentHashMap<JedisShardInfo,JedisFacade>();
    
    
    public void setRedisServers(String redisServers) {
		this.redisServers = redisServers;
	}

	/**
     * 10.103.124.223:6379@123,10.103.124.221:6379@123,10.103.124.222:6379@123
     * 
     * @param redisServers
     */
    private void init() {
        List<JedisShardInfo> hostAndPortList;
        if (null != redisServers && 0 < redisServers.length()) {
            final String[] hostDefs = redisServers.split(",");
            if (null != hostDefs && 1 <= hostDefs.length) {
                hostAndPortList = new ArrayList<JedisShardInfo>(
                        hostDefs.length);
                for (String hostDef : hostDefs) {
                    final String[] hostAndPort = hostDef.split(":");
                    if (null != hostAndPort && 2 == hostAndPort.length) {
                        JedisShardInfo hnp;
                        String host = hostAndPort[0];
                        int port = 6379;
                        final String[] portAndPwd = hostAndPort[1].split("@");
                        if (null != portAndPwd) {
                            port = Integer.parseInt(portAndPwd[0]);
                            hnp = new JedisShardInfo(host, port);
                            if (2 == portAndPwd.length) {
                                hnp.setPassword(portAndPwd[1]);
                            }
                        }
                        else{
                            hnp = new JedisShardInfo(host);
                        }
                        hnp.setTimeout(JEDIS_TIMEOUT);
                        hostAndPortList.add(hnp);
                    }
                }
                for (JedisShardInfo info : hostAndPortList) {
                    JedisFacade jedis =  new JedisFacadeImpl(info);
                    if (isHealthy(jedis)) {
                        handleHealthMember(info);
                        jedisMap.put(info, jedis);
                    } else {
                        handleSickMember(info);
                    }
                }
                electNewMaster();
                setHeartbeat(HEART_BEAT_CHECKER_INTERVAL);
            }
        }
    }
    
    public JedisShardInfo getMaster() {
        synchronized (lock) {
            return elected;
        }
    }
    
    public Collection<JedisShardInfo> getMembers() {
        synchronized (lock) {
            return Collections.unmodifiableCollection(healthMembers);
        }
    }
    
    public JedisShardInfo electNewMaster() {
    	logger.info(Thread.currentThread().getId()+"  enter elect new master.");
        synchronized (lock) {
            while (elected == null || !isHealthy(elected) || isSlave(elected)) {
        	//while (elected == null || !isHealthy(elected) ) {
                /*if (null != elected) {
                    handleSickMember(elected);
                }*/
            	logger.info(Thread.currentThread().getId()+"  into elect section.");
                elected = null;
                for (int i = 0; i < healthMembers.size(); i++) {
                    JedisShardInfo m = healthMembers.get(i);
                    if (isHealthy(m)) {
                        logger.info(Thread.currentThread().getId()+"  new master has been elected." + m.toString());
                        elected = m;
                        resetMaster(m);
                        return elected;
                    } else {
                        handleSickMember(m);
                    }
                }
                interRock.lock();
                try {
                    masterNotnull.await();
                } catch (InterruptedException e) {
                    logger.info(e.getMessage(), e);
                }
                finally{
                    interRock.unlock();
                }
            }
            return elected;
        }
    }

    public void handleSickMember(JedisShardInfo m) {
        if(!sickMembers.contains(m)){
            sickMembers.add(m);
        }
        healthMembers.remove(m);
    }
    public void handleHealthMember(JedisShardInfo m) {
        if(!healthMembers.contains(m)){
            healthMembers.add(m);
        }
        sickMembers.remove(m);
        interRock.lock();
        try {
            masterNotnull.signal();
        } catch (Exception e) {
            e.printStackTrace();
        }
        finally{
            interRock.unlock();
        }
    }
    
    private synchronized JedisFacade loadJedisCachedJedis(JedisShardInfo m){
        JedisFacade jedis = jedisMap.get(m);
        if(null == jedis){
            jedis = new JedisFacadeImpl(m);
            jedisMap.put(m, jedis);
        }
        return jedis;
    }
    
    public void resetMaster(JedisShardInfo newMaster) {
        synchronized (lock) {
            if (!healthMembers.contains(newMaster))
                throw new JedisException(newMaster.toString()
                        + " is not member of the cluster");
            for (JedisShardInfo m : healthMembers) {
                JedisFacade jedis = loadJedisCachedJedis(m);
                if (isHealthy(jedis)) {
                    if (m == newMaster) {
                        jedis.slaveofNoOne();
                    } else {
                        jedis.slaveof(newMaster.getHost(), newMaster.getPort());
                    }
                }
            }
        }
    }
    
    
    public boolean isHealthy(JedisShardInfo info) {
        JedisFacade jedis = jedisMap.get(info);
        return isHealthy(jedis);
    } 
    public boolean isSlave(JedisShardInfo info) {
        JedisFacade jedis = jedisMap.get(info);
        logger.info("is it slave?");
        return jedis.info("Replication").indexOf("role:master")<0;
    }
    
    public static boolean isHealthy(JedisFacade jedis) {
        try {
            return jedis.ping().equals("PONG");
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.info("jedis is sick:"+jedis);
            return false;
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
                        t.setName(HEALTH_CHECKER_THREAD_NAME);
                        return t;
                    }
                });
            }
            return this.executor;
        }
    }
    
    private final class HealthChecker implements Runnable {
        JedisFacade jedis;
        public void run() {
            try {
                for (int i = 0; i < sickMembers.size(); i++) {
                    JedisShardInfo m = sickMembers.get(i);
                    jedis = loadJedisCachedJedis(m);
                    if (isHealthy(jedis)) {
                        handleHealthMember(m);
                        if(elected!=null && !m.equals(elected)){
                            jedis.slaveof(elected.getHost(), elected.getPort());
                        }
                    } else {
                        jedisMap.remove(m);
                    }
                }
                for (int i = 0; i < healthMembers.size(); i++) {
                    JedisShardInfo m = healthMembers.get(i);
                    jedis =  loadJedisCachedJedis(m);
                    if (!isHealthy(jedis)) {
                        jedisMap.remove(m);
                        handleSickMember(m);
                    } 
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
