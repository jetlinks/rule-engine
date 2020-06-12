package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.executor.Input;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.List;

@AllArgsConstructor
public class EventBusInput implements Input {

    private final String instanceId;

    private final String nodeId;

    //输入节点
    private final List<ScheduleJob.Event> events;

    private final EventBus bus;

    @Override
    public Flux<RuleData> subscribe() {
        Flux<RuleData> input = bus.subscribe(RuleConstants.Topics.input(instanceId, nodeId), RuleData.class);

        if (!CollectionUtils.isEmpty(events)) {
            return Flux.fromIterable(events)
                    .map(event -> bus.subscribe(RuleConstants.Topics.event(instanceId, event.getSource(), event.getType()), RuleData.class))
                    .as(Flux::merge)
                    .mergeWith(input);
        }

        return input;
    }

    @Override
    public void close() {

    }
}
