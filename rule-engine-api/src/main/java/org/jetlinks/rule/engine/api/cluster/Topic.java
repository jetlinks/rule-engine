package org.jetlinks.rule.engine.api.cluster;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface Topic<T> {

    Flux<T> subscribe();

    Mono<Boolean> publish(T data);


}
