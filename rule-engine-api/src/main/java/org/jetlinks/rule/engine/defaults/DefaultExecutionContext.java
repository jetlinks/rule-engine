package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.Slf4jLogger;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;

@Getter
@Slf4j
public class DefaultExecutionContext extends AbstractExecutionContext {

    public DefaultExecutionContext(ScheduleJob job, EventBus eventBus, ConditionEvaluator evaluator) {
        super(job,
                eventBus,
                new Slf4jLogger("rule.engine." + job.getInstanceId() + "." + job.getNodeId()),
                new EventBusInput(job.getInstanceId(), job.getNodeId(), job.getEvents(), eventBus),
                new EventBusOutput(job.getInstanceId(), eventBus, job.getOutputs(), evaluator)
        );
    }

}
