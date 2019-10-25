package org.jetlinks.rule.engine.cluster.supports;

import org.jetlinks.core.cluster.ServerNode;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import org.jetlinks.rule.engine.api.cluster.ServerNodeHelper;
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
    public List<ServerNode> select(SchedulingRule rule, List<ServerNode> allNode) {
        return allNode.stream()
                .filter(ServerNodeHelper::isWorker)
                .collect(Collectors.toList());
    }
}
