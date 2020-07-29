package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
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

        return bus.subscribe(Subscription.of("rule-engine:" + nodeId, RuleConstants.Topics.input(instanceId, nodeId),Subscription.Feature.values()), RuleData.class);
    }

}
