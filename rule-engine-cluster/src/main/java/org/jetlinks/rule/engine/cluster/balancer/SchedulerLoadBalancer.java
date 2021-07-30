package org.jetlinks.rule.engine.cluster.balancer;

import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.cluster.RuleInstance;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 调度器负载均衡器,用于对任务进行负载均衡
 *
 * @author zhouhao
 * @since 1.0.4
 */
public interface SchedulerLoadBalancer {

    /**
     * 重新对任务进行负载均衡
     *
     * @param schedulers 调度器
     * @param balanceAll 是否对全部任务进行再分配
     * @return empty void
     */
    Mono<Void> reBalance(List<Scheduler> schedulers, boolean balanceAll);

    /**
     * 使用指定的调度器,对指定对规则进行重新分配，比如新加入的调度器,需要进行reBalance才会调度自己新的任务
     *
     * @param schedulers 调度器
     * @param balanceAll 是否对全部任务进行再分配
     * @param instance   规则实例
     * @return void
     * @since 1.1.7
     */
    Mono<Void> reBalance(List<Scheduler> schedulers, RuleInstance instance, boolean balanceAll);

}
