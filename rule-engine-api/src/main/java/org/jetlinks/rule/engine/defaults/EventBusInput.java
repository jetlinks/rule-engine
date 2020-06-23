package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.Input;
import reactor.core.publisher.Flux;

@AllArgsConstructor
public class EventBusInput implements Input {

    private final String instanceId;

    private final String nodeId;

    private final EventBus bus;

    @Override
    public Flux<RuleData> accept() {

        return bus.subscribe(RuleConstants.Topics.input(instanceId, nodeId), RuleData.class);
    }

}
