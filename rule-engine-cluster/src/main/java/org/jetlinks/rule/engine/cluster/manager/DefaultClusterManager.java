package org.jetlinks.rule.engine.cluster.manager;

import org.jetlinks.rule.engine.cluster.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DefaultClusterManager implements ClusterManager {

    private NodeInfo currentNode;

    private Map<String, NodeInfo> allNode;


    @Override
    public NodeInfo getCurrentNode() {
        return currentNode;
    }

    @Override
    public List<NodeInfo> getAllAliveNode() {
        return new ArrayList<>(allNode.values());
    }

    @Override
    public ClusterLock getLock(String lockName, long timeout, TimeUnit timeUnit) {
        return null;
    }

    @Override
    public <K, V> ClusterMap<K, V> getMap(String name) {
        return null;
    }

    @Override
    public <T> Queue<T> getQueue(String name) {
        return null;
    }

    @Override
    public <T> Topic<T> getTopic(String name) {
        return null;
    }

    @Override
    public ClusterSemaphore getSemaphore(String name) {
        return null;
    }
}
