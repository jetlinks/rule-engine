package org.jetlinks.rule.engine.api.cluster;


import java.util.List;

public interface WorkerNodeSelector {

    List<NodeInfo> select(SchedulingRule rule, List<NodeInfo> allNode);

}
