package org.jetlinks.rule.engine.cluster.balancer;

import org.jetlinks.rule.engine.api.Scheduler;
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
     * @param schedulers 全部可用调度器
     * @param balanceAll 是否对全部任务进行再分配
     * @return empty void
     */
    Mono<Void> reBalance(List<Scheduler> schedulers, boolean balanceAll);

}
