package org.jetlinks.rule.engine.cluster.redisson;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.cluster.*;
import org.jetlinks.rule.engine.api.cluster.ha.HaManager;
import org.redisson.api.RSemaphore;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private String name = "rule:engine";

    private Map<String, RedissonQueue> queueMap = new ConcurrentHashMap<>();
    private Map<String, Topic> topicMap = new ConcurrentHashMap<>();

    private RTopic queueTakeTopic;

    @Override
    public NodeInfo getCurrentNode() {
        return haManager.getCurrentNode();
    }

    @Override
    public List<NodeInfo> getAllAliveNode() {
        return haManager.getAllAliveNode();
    }

    protected String getRedisKey(String type, String key) {
        return name + ":" + type + ":" + key;
    }

    protected String getRedisKey(String key) {
        return name + ":" + key;
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
    public <T> Queue<T> getQueue(String name) {
        return queueMap.computeIfAbsent(name, n -> {
            RedissonQueue<T> queue = new RedissonQueue<T>(redissonClient.getQueue(getRedisKey("queue", n))) {
                @Override
                public void put(T data) {
                    super.put(data);
                    queueTakeTopic.publish(n);
                }

                @Override
                public CompletionStage<Boolean> putAsync(T data) {
                    return super.putAsync(data)
                            .thenApply(success -> {
                                queueTakeTopic.publish(n);
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
        rSemaphore.trySetPermitsAsync(permits);
        rSemaphore.expireAsync(12, TimeUnit.HOURS);
        return rSemaphore;
    }

}
