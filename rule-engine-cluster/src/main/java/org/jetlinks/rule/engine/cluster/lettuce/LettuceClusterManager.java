package org.jetlinks.rule.engine.cluster.lettuce;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.lettuce.LettucePlus;
import org.jetlinks.rule.engine.api.cluster.*;
import org.jetlinks.rule.engine.api.cluster.ha.HaManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("all")
public class LettuceClusterManager implements ClusterManager {

    private LettucePlus plus;

    private Map<String, Queue> queueMap = new ConcurrentHashMap<>();

    private Map<String, Topic> topicMap = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private HaManager haManager;

    @Getter
    @Setter
    private String name = "rule:engine";

    public LettuceClusterManager(LettucePlus plus) {
        this.plus = plus;
    }

    @Override
    public NodeInfo getCurrentNode() {
        return getHaManager().getCurrentNode();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public HaManager getHaManager() {
        return haManager;
    }

    @Override
    public List<NodeInfo> getAllAliveNode() {
        return haManager.getAllAliveNode();
    }

    @Override
    public ClusterLock getLock(String lockName, long timeout, TimeUnit timeUnit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <K, V> ClusterMap<K, V> getMap(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Queue<T> getQueue(String name) {
        return queueMap.computeIfAbsent(name, _id -> new LettuceQueue<>(plus.getQueue(_id)));
    }

    @Override
    public <T> Topic<T> getTopic(Class<T> type, String name) {
        return topicMap.computeIfAbsent(name, _id -> new LettuceTopic<>(type, plus.getTopic(_id)));
    }

    @Override
    public ClusterSemaphore getSemaphore(String name, int permits) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> ClusterObject<T> getObject(String name) {
        throw new UnsupportedOperationException();
    }


}
