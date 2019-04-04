package org.jetlinks.rule.engine.cluster.redisson;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.cluster.*;
import org.jetlinks.rule.engine.cluster.ha.HaManager;
import org.redisson.api.*;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SuppressWarnings("all")
@Slf4j
public class RedissonClusterManager implements ClusterManager {


    @Setter
    @Getter
    private HaManager haManager;

    @Getter
    @Setter
    private RedissonClient redissonClient;

    private String prefix = "rule:engine";

    private Map<String, Queue> queueMap = new ConcurrentHashMap<>();
    private Map<String, Topic> topicMap = new ConcurrentHashMap<>();

    @Override
    public NodeInfo getCurrentNode() {
        return haManager.getCurrentNode();
    }

    @Override
    public HaManager getHaManager() {
        return haManager;
    }

    @Override
    public List<NodeInfo> getAllAliveNode() {
        return haManager.getAllAliveNode();
    }

    protected String getRedisKey(String type, String key) {
        return prefix + ":" + type + ":" + key;
    }

    protected String getRedisKey(String key) {
        return prefix + ":" + key;
    }

    public Map<String, RSemaphore> semaphoreMap = new ConcurrentHashMap<>();

    public void shutdown() {
        for (RSemaphore value : semaphoreMap.values()) {
            if (value.availablePermits() == 0) {
                value.release();
            }
        }
    }

    @Override
    @SneakyThrows
    public ClusterLock getLock(String lockName, long timeout, TimeUnit timeUnit) {

        RSemaphore rSemaphore = getRSemaphore(getRedisKey("lock", lockName), 1);
        boolean success = rSemaphore.tryAcquire(timeout, timeUnit);
        if (!success) {
            throw new TimeoutException("get lock timeout");
        }
        return new RedissonLock(rSemaphore);
    }

    @Override
    public <K, V> ClusterMap<K, V> getMap(String name) {
        return new RedissonClusterMap<>(redissonClient.getMap(getRedisKey("map", name)));
    }

    @Override
    public <T> Queue<T> getQueue(String name) {
        return queueMap.computeIfAbsent(name, n -> {
            RedissonQueue<T> queue = new RedissonQueue<>(redissonClient.getBlockingQueue(getRedisKey("queue", n)));
            return queue;
        });
    }

    @Override
    public <T> Topic<T> getTopic(Class<T> type, String name) {
        return topicMap.computeIfAbsent(name, n -> {
            RedissonTopic<T> topic = new RedissonTopic<>(redissonClient.getTopic(getRedisKey("topic", name)), type);
            topic.start();
            return topic;
        });
    }

    protected RSemaphore getRSemaphore(String key, int permits) {
        RSemaphore rSemaphore = semaphoreMap.computeIfAbsent(key, name -> {
            return redissonClient.getSemaphore(name);
        });
        if (!rSemaphore.isExists()) {
            semaphoreMap.put(key, rSemaphore = redissonClient.getSemaphore(key));
        }
        rSemaphore.trySetPermits(permits);
        rSemaphore.expire(12, TimeUnit.HOURS);
        return rSemaphore;
    }

    @Override
    public ClusterSemaphore getSemaphore(String name, int permits) {
        String key = getRedisKey("semaphore", name);

        return new RedissonSemaphore(getRSemaphore(key, permits)) {
            @Override
            public boolean delete() {
                semaphoreMap.remove(key);
                return super.delete();
            }
        };
    }

    @Override
    public <T> ClusterObject<T> getObject(String name) {
        return new RedissonClusterObject<>(redissonClient.getBucket(getRedisKey("object", name)));
    }
}
