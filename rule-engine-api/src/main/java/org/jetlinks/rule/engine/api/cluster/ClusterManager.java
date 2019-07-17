package org.jetlinks.rule.engine.api.cluster;

import org.jetlinks.rule.engine.api.cluster.ha.HaManager;

import java.util.List;

public interface ClusterManager {

    NodeInfo getCurrentNode();

    String getName();

    HaManager getHaManager();

    List<NodeInfo> getAllAliveNode();

    <T> Queue<T> getQueue(String name);

    <T> Topic<T> getTopic(Class<T> type, String name);

}
