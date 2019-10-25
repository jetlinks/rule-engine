package org.jetlinks.rule.engine.cluster;

import org.jetlinks.core.cluster.ServerNode;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;

import java.util.List;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface WorkerNodeSelectorStrategy {
    String getType();

    List<ServerNode> select(SchedulingRule rule, List<ServerNode> allNode);
}
