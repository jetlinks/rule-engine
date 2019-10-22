package org.jetlinks.rule.engine.cluster.lettuce;

import org.jetlinks.lettuce.RedisTopic;
import org.jetlinks.rule.engine.api.cluster.Topic;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LettuceTopic<T> implements Topic<T> {

    private RedisTopic<T> topic;

    private Class<T> type;


    public LettuceTopic(Class<T> type, RedisTopic<T> topic) {
        this.topic = topic;
        this.type = type;
    }

    @Override
    public reactor.core.publisher.Flux<T> subscribe() {
        return Flux.create(sink -> {
            topic.addListener((topic, data) -> {
                sink.next(data);
            });
        });
    }

    @Override
    public reactor.core.publisher.Mono<Boolean> publish(T data) {

        return Mono.fromCompletionStage(topic.publish(data))
                .map(i -> i > 0);
    }
}
