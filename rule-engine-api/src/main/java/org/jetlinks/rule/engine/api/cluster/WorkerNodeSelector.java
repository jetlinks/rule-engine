package org.jetlinks.rule.engine.api.cluster;


import org.jetlinks.core.cluster.ServerNode;

import java.util.List;

public interface WorkerNodeSelector {

    List<ServerNode> select(SchedulingRule rule, List<ServerNode> allNode);

}
