package org.jetlinks.rule.engine.cluster.worker;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.lang.SeparatedCharSequence;
import org.jetlinks.core.lang.SeparatedString;
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
    public static final SeparatedCharSequence LOG_TEMPLATE =
        SeparatedString.create('.', "rule", "engine", "*", "*");

    @Deprecated
    public ClusterExecutionContext(String workerId,
                                   ScheduleJob scheduleJob,
                                   EventBus eventBus,
                                   ClusterManager clusterManager,
                                   ConditionEvaluator evaluator) {
        super(workerId,
              scheduleJob,
              eventBus,
              new Slf4jLogger(LOG_TEMPLATE.append(scheduleJob.getInstanceId()).append(scheduleJob.getNodeId())),
              job -> new QueueInput(job.getInstanceId(), job.getNodeId(), clusterManager),
              job -> new QueueOutput(job.getInstanceId(), clusterManager, job.getOutputs(), evaluator),
              job -> job.getEventOutputs()
                        .stream()
                        .map(event -> new QueueEventOutput(job.getInstanceId(), clusterManager, event.getType(), event.getSource()))
                        .collect(Collectors.groupingBy(QueueEventOutput::getEvent, Collectors.collectingAndThen(Collectors.toList(), CompositeOutput::of))),
              () -> new ClusterGlobalScope(clusterManager)
        );
    }


    public ClusterExecutionContext(ScheduleJob scheduleJob,
                                   EventBus eventBus,
                                   RuleIOManager manager,
                                   RuleMonitorManager monitorManager) {
        super(scheduleJob,
              eventBus,
              monitorManager.createMonitor(scheduleJob),
              manager::createInput,
              manager::createOutput,
              manager::createEvent,
              manager::createScope
        );
    }

}
