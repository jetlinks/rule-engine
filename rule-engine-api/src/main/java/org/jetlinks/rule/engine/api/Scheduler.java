package org.jetlinks.rule.engine.api;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

public interface Scheduler {

    List<Worker> getWorkers();

    Optional<Worker> getWorker(String workerId);

    Flux<Worker> handleWorkerJoin();

    Flux<Worker> handleWorkerLeave();

}
