package org.jetlinks.rule.engine.api.cluster;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Queue<T> {
    Flux<T> poll();

    Mono<Boolean> put(Publisher<T> data);

    Mono<Boolean> start();

    Mono<Boolean> stop();

    void setLocalConsumerPoint(float point);

}
