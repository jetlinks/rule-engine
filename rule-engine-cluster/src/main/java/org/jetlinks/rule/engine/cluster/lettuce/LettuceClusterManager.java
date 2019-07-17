package org.jetlinks.rule.engine.cluster.lettuce;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.lettuce.LettucePlus;
import org.jetlinks.rule.engine.api.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.api.cluster.Queue;
import org.jetlinks.rule.engine.api.cluster.Topic;
import org.jetlinks.rule.engine.api.cluster.ha.HaManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    public <T> Queue<T> getQueue(String name) {
        return queueMap.computeIfAbsent(name, _id -> new LettuceQueue<>(plus.getQueue(_id)));
    }

    @Override
    public <T> Topic<T> getTopic(Class<T> type, String name) {
        return topicMap.computeIfAbsent(name, _id -> new LettuceTopic<>(type, plus.getTopic(_id)));
    }

}
