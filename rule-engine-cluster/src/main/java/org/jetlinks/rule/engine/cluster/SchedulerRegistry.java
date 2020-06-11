package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.Scheduler;
import org.jetlinks.rule.engine.api.executor.ScheduleJob;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SchedulerRegistry {
    /**
     * 获取全部Scheduler
     *
     * @return scheduler流
     */
    Flux<ClusterScheduler> getSchedulers();

    /**
     * 监听Scheduler加入注册中心事件
     *
     * @return scheduler流
     */
    Flux<ClusterScheduler> handleSchedulerJoin();

    /**
     * 监听Scheduler掉线事件
     *
     * @return scheduler流
     */
    Flux<ClusterScheduler> handleSchedulerLeave();

    /**
     * 注册Scheduler到注册中心
     *
     * @param worker scheduler
     * @return empty mono
     */
    Mono<Void> register(Scheduler worker);

}
