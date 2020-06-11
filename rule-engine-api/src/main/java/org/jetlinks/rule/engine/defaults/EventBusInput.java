package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.events.EventBus;
import org.jetlinks.rule.engine.api.executor.Input;
import org.jetlinks.rule.engine.api.executor.ScheduleJob;
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
        Flux<RuleData> input = bus.subscribe("/rule/engine/" + instanceId + "/" + nodeId + "/input", RuleData.class);

        if (events != null) {
            return Flux.fromIterable(events)
                    .map(event -> bus.subscribe("/rule/engine/" + instanceId + "/" + event.getSource() + "/event/" + event.getType(), RuleData.class))
                    .as(Flux::merge)
                    .mergeWith(input);
        }

        return input;
    }

    @Override
    public void close() {

    }
}
