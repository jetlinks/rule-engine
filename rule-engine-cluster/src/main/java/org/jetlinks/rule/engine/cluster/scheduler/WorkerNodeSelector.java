package org.jetlinks.rule.engine.cluster.scheduler;

import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.cluster.NodeInfo;

import java.util.List;

public interface WorkerNodeSelector {

    List<NodeInfo> select(SchedulingRule rule, List<NodeInfo> allNode);

}
