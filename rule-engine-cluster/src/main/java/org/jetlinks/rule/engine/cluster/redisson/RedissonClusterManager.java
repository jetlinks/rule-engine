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
public class RedissonClusterManager implements ClusterManager, HaManager {

    @Getter
    @Setter
    private NodeInfo currentNode;

    @Getter
    @Setter
    private RedissonClient redissonClient;

    @Getter
    @Setter
    private ScheduledExecutorService executorService;

    private String prefix = "rule:engine";

    private Map<String, Queue> queueMap = new ConcurrentHashMap<>();
    private Map<String, Topic> topicMap = new ConcurrentHashMap<>();

    protected RPatternTopic clusterNodeTopic;

    protected RTopic clusterNodeKeepTopic;
    protected RTopic clusterNodeLeaveTopic;

    @Setter
    @Getter
    private long timeToLeave = 10;

    private RMap<String, NodeInfo> allNodeInfo;
    private Map<String, NodeInfo>  localAllNode;

    protected void nodeJoin(NodeInfo nodeInfo) {
        if (nodeInfo.getId().equals(currentNode.getId())) {
            return;
        }
        nodeInfo.setLastKeepAliveTime(System.currentTimeMillis());
        localAllNode.put(nodeInfo.getId(), nodeInfo);
        allNodeInfo.put(nodeInfo.getId(), nodeInfo);
        log.info("node join:{}", nodeInfo);
    }

    protected void nodeLeave(NodeInfo nodeInfo) {
        if (nodeInfo.getId().equals(currentNode.getId())) {
            return;
        }
        localAllNode.remove(nodeInfo.getId());
        allNodeInfo.fastRemove(nodeInfo.getId());
        log.info("node leave:{}", nodeInfo);
    }

    public void start() {
        Assert.notNull(redissonClient, "redissonClient");
        Assert.notNull(currentNode, "currentNode");
        Assert.notNull(currentNode.getId(), "currentNode.id");
        Assert.notNull(executorService, "executorService");

        allNodeInfo = redissonClient.getMap(getRedisKey("cluster:nodes"));
        //注册自己
        allNodeInfo.put(currentNode.getId(), currentNode);

        localAllNode = new HashMap<>(allNodeInfo);

        clusterNodeTopic = redissonClient.getPatternTopic(getRedisKey("cluster:node:*"));
        clusterNodeKeepTopic = redissonClient.getTopic(getRedisKey("cluster:node:keep"));
        clusterNodeLeaveTopic = redissonClient.getTopic(getRedisKey("cluster:node:leave"));

        //订阅节点上下线
        clusterNodeTopic.addListener(NodeInfo.class, (pattern, channel, msg) -> {
            String operation = String.valueOf(channel);
            if (getRedisKey("cluster:node:join").equals(operation)) {
                nodeJoin(msg);
            } else if (getRedisKey("cluster:node:leave").equals(operation)) {
                nodeLeave(msg);
            } else if (getRedisKey("cluster:node:keep").equals(operation)) {
                NodeInfo nodeInfo = localAllNode.get(msg.getId());
                if (nodeInfo == null) {
                    nodeJoin(msg);
                } else {
                    nodeInfo.setLastKeepAliveTime(System.currentTimeMillis());
                }
            } else {
                log.info("unkown channel:{} {}", operation, msg);
            }
        });

        executorService.scheduleAtFixedRate(() -> {
            //保活
            currentNode.setLastKeepAliveTime(System.currentTimeMillis());
            clusterNodeKeepTopic.publish(currentNode);
            //注册自己
            allNodeInfo.put(currentNode.getId(), currentNode);

            //检查节点是否存活
            localAllNode
                    .values()
                    .stream()
                    .filter(info -> System.currentTimeMillis() - info.getLastKeepAliveTime() > timeToLeave)
                    .forEach(clusterNodeLeaveTopic::publish);
        }, 1, Math.min(2, timeToLeave), TimeUnit.SECONDS);
    }


    @Override
    public List<NodeInfo> getAllAliveNode() {
        return new ArrayList<>(localAllNode.values());
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
            queue.start();
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
        return new RedissonSemaphore(getRSemaphore(getRedisKey("semaphore", name), permits));
    }
}
