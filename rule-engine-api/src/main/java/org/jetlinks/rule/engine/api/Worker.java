package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import reactor.core.publisher.Mono;

import java.util.List;

public interface Worker {

    String getId();

    String getName();

    Mono<Executor> createExecutor(RuleNodeConfiguration configuration);

    Mono<List<String>> getSupportExecutors();

    Mono<State> getState();

    enum State {
        working,
        shutdown,
        timeout;
    }
}
