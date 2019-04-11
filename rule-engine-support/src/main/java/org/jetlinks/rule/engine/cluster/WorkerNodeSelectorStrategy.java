package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;

import java.util.List;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface WorkerNodeSelectorStrategy {
    String getType();

    List<NodeInfo> select(SchedulingRule rule, List<NodeInfo> allNode);
}
