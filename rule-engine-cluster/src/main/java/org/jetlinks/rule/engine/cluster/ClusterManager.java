package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.cluster.ha.HaManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface ClusterManager {

    NodeInfo getCurrentNode();

    String getName();

    HaManager getHaManager();

    List<NodeInfo> getAllAliveNode();

    ClusterLock getLock(String lockName, long timeout, TimeUnit timeUnit);

    <K, V> ClusterMap<K, V> getMap(String name);

    <T> Queue<T> getQueue(String name);

    <T> Topic<T> getTopic(Class<T> type, String name);

    ClusterSemaphore getSemaphore(String name, int permits);

    <T> ClusterObject<T> getObject(String name);
}
