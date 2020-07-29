package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.rule.engine.api.Slf4jLogger;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scope.GlobalScope;
import org.jetlinks.rule.engine.api.task.CompositeOutput;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;

import java.util.stream.Collectors;

@Getter
@Slf4j
public class DefaultExecutionContext extends AbstractExecutionContext {

    public DefaultExecutionContext(String workerId,
                                   ScheduleJob job,
                                   EventBus eventBus,
                                   ConditionEvaluator evaluator,
                                   GlobalScope scope) {
        super(workerId, job,
                eventBus,
                new Slf4jLogger("rule.engine." + job.getInstanceId() + "." + job.getNodeId()),
                new EventBusInput(job.getInstanceId(), job.getNodeId(), eventBus),
                new EventBusOutput(job.getInstanceId(), eventBus, job.getOutputs(), evaluator),
                job.getEventOutputs()
                        .stream()
                        .map(event -> new EventBusEventOutput(job.getInstanceId(), eventBus, event.getType(), event.getSource()))
                        .collect(Collectors.groupingBy(EventBusEventOutput::getEvent, Collectors.collectingAndThen(Collectors.toList(), CompositeOutput::of))),
                scope
        );
    }

}
