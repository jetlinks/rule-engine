package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.Output;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Slf4j
@AllArgsConstructor
public class EventBusEventOutput implements Output {

    private final String instanceId;

    private final EventBus eventBus;

    @Getter
    private final String event;

    private final String sourceNode;

    @Override
    public Mono<Boolean> write(Publisher<RuleData> dataStream) {
        return eventBus.publish(createTopic(sourceNode), dataStream)
                .thenReturn(true);
    }

    @Override
    public Mono<Void> write(String nodeId, Publisher<RuleData> data) {
        return eventBus.publish(createTopic(nodeId), data).then();
    }

    private String createTopic(String node) {
        return RuleConstants.Topics.input(instanceId, node);
    }

}
