package org.jetlinks.rule.engine.api;

import reactor.core.publisher.Flux;

public interface WorkerSelector {

    Flux<Worker> select(Flux<Worker> workers);

}
