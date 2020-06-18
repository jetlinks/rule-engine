package org.jetlinks.rule.engine.cluster.worker;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.Slf4jLogger;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;
import org.jetlinks.rule.engine.defaults.AbstractExecutionContext;

@Getter
@Slf4j
public class ClusterExecutionContext extends AbstractExecutionContext {

    public ClusterExecutionContext(ScheduleJob job,
                                   EventBus eventBus,
                                   ClusterManager clusterManager,
                                   ConditionEvaluator evaluator) {
        super(
                job,
                eventBus,
                new Slf4jLogger("rule.engine." + job.getInstanceId() + "." + job.getNodeId()),
                new QueueInput(job.getInstanceId(), job.getNodeId(), job.getEvents(), clusterManager),
                new QueueOutput(job.getInstanceId(), clusterManager, job.getOutputs(), evaluator)
        );
    }


}
