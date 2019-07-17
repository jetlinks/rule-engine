package org.jetlinks.rule.engine.cluster.supports;

import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import org.jetlinks.rule.engine.cluster.WorkerNodeSelectorStrategy;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultWorkerNodeSelectorStrategy implements WorkerNodeSelectorStrategy {
    @Override
    public String getType() {
        return "default";
    }

    @Override
    public List<NodeInfo> select(SchedulingRule rule, List<NodeInfo> allNode) {
        return allNode.stream()
                .filter(NodeInfo::isWorker)
                .collect(Collectors.toList());
    }
}
