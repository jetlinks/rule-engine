package org.jetlinks.rule.engine.cluster.redisson;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.cluster.*;
import org.jetlinks.rule.engine.cluster.Queue;
import org.jetlinks.rule.engine.cluster.ha.HaManager;
import org.redisson.api.*;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("all")
@Slf4j
public class RedissonClusterManager implements ClusterManager {


    @Setter
    @Getter
    private HaManager haManager;

    @Getter
    @Setter
    private RedissonClient redissonClient;

    @Getter
    @Setter
    private ScheduledExecutorService executorService;

    @Getter
    @Setter
    private String prefix = "rule:engine";

    private Map<String, RedissonQueue> queueMap = new ConcurrentHashMap<>();
    private Map<String, Topic> topicMap = new ConcurrentHashMap<>();

    private RTopic queueTakeTopic;

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

    public void start() {

        queueTakeTopic = redissonClient.getTopic(getRedisKey("queue:take"));
        queueTakeTopic.addListener(String.class, ((channel, msg) -> {
            Optional.ofNullable(queueMap.get(msg))
                    .ifPresent(RedissonQueue::flush);
        }));

        executorService.scheduleAtFixedRate(() -> {
            queueMap.values().forEach(RedissonQueue::flush);
        }, 1, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        for (RSemaphore value : semaphoreMap.values()) {
            if (value.availablePermits() == 0) {
                value.release();
            }
        }
        for (RedissonQueue redissonQueue : queueMap.values()) {
            redissonQueue.stop();
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
            RedissonQueue<T> queue = new RedissonQueue<T>(redissonClient.getQueue(getRedisKey("queue", n))) {
                @Override
                public void put(T data) {
                    super.put(data);
                    queueTakeTopic.publishAsync(n);
                }

                @Override
                public CompletionStage<Boolean> putAsync(T data) {
                    return super.putAsync(data)
                            .thenApply(success -> {
                                queueTakeTopic.publishAsync(n);
                                return success;
                            });
                }
            };
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
