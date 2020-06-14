package org.jetlinks.rule.engine.api.worker;

import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import reactor.core.publisher.Flux;

/**
 * 工作节点选择器,用来选择合适的节点来执行任务
 */
public interface WorkerSelector {

    Flux<Worker> select(Flux<Worker> workers, ScheduleJob job);

}
