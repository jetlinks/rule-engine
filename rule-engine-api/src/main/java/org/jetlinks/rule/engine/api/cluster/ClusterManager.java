package org.jetlinks.rule.engine.api.cluster;

import org.jetlinks.rule.engine.api.cluster.ha.HaManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface ClusterManager {

    NodeInfo getCurrentNode();

    String getName();

    HaManager getHaManager();

    List<NodeInfo> getAllAliveNode();

    @Deprecated
    ClusterLock getLock(String lockName, long timeout, TimeUnit timeUnit);

    @Deprecated
    <K, V> ClusterMap<K, V> getMap(String name);

    <T> Queue<T> getQueue(String name);

    <T> Topic<T> getTopic(Class<T> type, String name);

    @Deprecated
    ClusterSemaphore getSemaphore(String name, int permits);

    @Deprecated
    <T> ClusterObject<T> getObject(String name);
}
