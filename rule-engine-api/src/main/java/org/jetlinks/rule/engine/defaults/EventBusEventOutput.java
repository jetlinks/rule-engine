package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.Output;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Slf4j
@AllArgsConstructor
public class EventBusEventOutput implements Output {

    protected final String instanceId;

    protected final EventBus eventBus;

    @Getter
    protected final String event;

    protected final String sourceNode;

    @Override
    public Mono<Boolean> write(RuleData data) {
        return eventBus
            .publish(createTopic(sourceNode), data)
            // 有订阅者则认为成功
            .map(i -> i > 0);
    }

    @Override
    public Mono<Boolean> write(Publisher<RuleData> dataStream) {
        return eventBus
            .publish(createTopic(sourceNode), dataStream)
            // 有订阅者则认为成功
            .map(i -> i > 0);
    }

    @Override
    public Mono<Void> write(String nodeId, Publisher<RuleData> data) {
        return eventBus.publish(createTopic(nodeId), data).then();
    }

    protected CharSequence createTopic(String node) {
        return RuleConstants.Topics.input(instanceId, node);
    }

}
