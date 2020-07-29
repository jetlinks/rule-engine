package org.jetlinks.rule.engine.cluster.worker;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.rule.engine.api.Slf4jLogger;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.CompositeOutput;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;
import org.jetlinks.rule.engine.cluster.scope.ClusterGlobalScope;
import org.jetlinks.rule.engine.defaults.AbstractExecutionContext;

import java.util.stream.Collectors;

@Getter
@Slf4j
public class ClusterExecutionContext extends AbstractExecutionContext {

    public ClusterExecutionContext(String workerId,
                                   ScheduleJob job,
                                   EventBus eventBus,
                                   ClusterManager clusterManager,
                                   ConditionEvaluator evaluator) {
        super(workerId,
                job,
                eventBus,
                new Slf4jLogger("rule.engine." + job.getInstanceId() + "." + job.getNodeId()),
                new QueueInput(job.getInstanceId(), job.getNodeId(), clusterManager),
                new QueueOutput(job.getInstanceId(), clusterManager, job.getOutputs(), evaluator),
                job.getEventOutputs()
                        .stream()
                        .map(event -> new QueueEventOutput(job.getInstanceId(), clusterManager, event.getType(), event.getSource()))
                        .collect(Collectors.groupingBy(QueueEventOutput::getEvent, Collectors.collectingAndThen(Collectors.toList(), CompositeOutput::of))),
                new ClusterGlobalScope(clusterManager)
        );
    }


}
