package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import org.jetlinks.rule.engine.api.cluster.WorkerNodeSelector;
import org.jetlinks.rule.engine.cluster.supports.DefaultWorkerNodeSelectorStrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultWorkerNodeSelector implements WorkerNodeSelector {
    private Map<String, WorkerNodeSelectorStrategy> allStrategy = new HashMap<>();

    private WorkerNodeSelectorStrategy defaultStrategy = new DefaultWorkerNodeSelectorStrategy();

    public DefaultWorkerNodeSelector() {
        register(defaultStrategy);
    }

    @Override
    public List<NodeInfo> select(SchedulingRule rule, List<NodeInfo> allNode) {
        return Optional
                .ofNullable(rule)
                .map(SchedulingRule::getType)
                .map(type -> allStrategy.get(type))
                .orElse(defaultStrategy)
                .select(rule, allNode);
    }

    public void register(WorkerNodeSelectorStrategy strategy) {
        allStrategy.put(strategy.getType(), strategy);
    }
}
