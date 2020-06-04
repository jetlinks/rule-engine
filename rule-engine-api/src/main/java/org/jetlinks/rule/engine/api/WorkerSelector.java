package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import reactor.core.publisher.Flux;

public interface WorkerSelector {

    Flux<Worker> select(Flux<Worker> workers, SchedulingRule rule);

}
