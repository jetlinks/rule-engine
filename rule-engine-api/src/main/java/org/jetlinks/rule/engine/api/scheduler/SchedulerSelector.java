package org.jetlinks.rule.engine.api.scheduler;

import reactor.core.publisher.Flux;

public interface SchedulerSelector {

    SchedulerSelector selectAll = (scheduler, job) -> scheduler;

    Flux<Scheduler> select(Flux<Scheduler> schedulers, ScheduleJob job);

}
