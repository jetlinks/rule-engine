package org.jetlinks.rule.engine.defaults;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
public class EventBusOutput extends AbstractOutput {

    private final EventBus eventBus;

    public EventBusOutput(String instanceId,
                          EventBus eventBus,
                          List<ScheduleJob.Output> outputs,
                          ConditionEvaluator evaluator) {
        super(instanceId, outputs, evaluator);
        this.eventBus = eventBus;
    }


    @Override
    protected Mono<Boolean> doWrite(CharSequence address, Publisher<RuleData> data) {
        return eventBus.publish(address, data).then(Reactors.ALWAYS_TRUE);
    }

    @Override
    protected Mono<Boolean> doWrite(CharSequence address, RuleData data) {
        return eventBus.publish(address, data).then(Reactors.ALWAYS_TRUE);
    }


}
