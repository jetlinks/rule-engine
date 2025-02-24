package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.Input;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@AllArgsConstructor
public class EventBusInput implements Input {

    protected final String instanceId;

    protected final String nodeId;

    protected final EventBus bus;

    static final Subscription.Feature[] features = {
        Subscription.Feature.local,
        Subscription.Feature.broker,
        Subscription.Feature.shared,
        Subscription.Feature.sharedLocalFirst
    };

    @Override
    public Flux<RuleData> accept() {

        return bus.subscribe(Subscription.of(
                                 "rule-engine:" + nodeId,
                                 RuleConstants.Topics.input(instanceId, nodeId),
                                 features),
                             RuleData.class);
    }

    @Override
    public Disposable accept(Function<RuleData, Mono<Boolean>> listener) {
        return bus.subscribe(Subscription.of(
                                 "rule-engine:" + nodeId,
                                 RuleConstants.Topics.input(instanceId, nodeId),
                                 features),
                             topic -> listener
                                 .apply(topic.decode(RuleData.class))
                                 .then());
    }
}
