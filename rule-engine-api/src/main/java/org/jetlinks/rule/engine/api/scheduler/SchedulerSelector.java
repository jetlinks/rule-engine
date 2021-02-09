package org.jetlinks.rule.engine.api.scheduler;

import reactor.core.publisher.Flux;

/**
 * 调度器选择器,根据任务,从多个调度器中选择调度器来执行此任务
 *
 * @author zhouhao
 * @since 1.1
 */
public interface SchedulerSelector {

    SchedulerSelector selectAll = (scheduler, job) -> scheduler;

    /**
     * 选择调度器,可通过实现此方法来进行任务负载均衡,或者根据任务指定的调度规则来选择不同的调度器
     *
     * @param schedulers 所有可用调度器
     * @param job        任务信息
     * @return 执行此任务的调度器
     */
    Flux<Scheduler> select(Flux<Scheduler> schedulers, ScheduleJob job);

}
