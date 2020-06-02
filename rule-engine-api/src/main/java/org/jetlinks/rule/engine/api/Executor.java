package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import reactor.core.publisher.Mono;

public interface Executor {

    String getId();

    String getKey();

    String getName();

    Mono<Void> start(ExecutionContext context);

    Mono<Void> pause();

    Mono<Void> resume();

    Mono<State> getState();


    enum State {
        running,
        paused
    }
}
