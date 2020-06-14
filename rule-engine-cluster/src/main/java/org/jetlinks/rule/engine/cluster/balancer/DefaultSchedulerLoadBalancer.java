package org.jetlinks.rule.engine.cluster.balancer;

import lombok.Setter;
import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.cluster.SchedulerRegistry;
import org.jetlinks.rule.engine.cluster.TaskSnapshotRepository;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

public class DefaultSchedulerLoadBalancer implements SchedulerLoadBalancer {

    //均衡器ID,集群全局唯一
    private String id;

    //集群leaderId
    private String leaderId;

    //上线时间
    private long uptime;

    @Setter
    private boolean autoReBalance = true;

    private EventBus eventBus;

    private SchedulerRegistry registry;

    private TaskSnapshotRepository snapshotRepository;

    public void start() {
        uptime = System.currentTimeMillis();

        if (!autoReBalance) {
            return;
        }

        //订阅调度器离线事件,进行自动负载均衡
        registry.handleSchedulerLeave()
                .subscribe(scheduler -> {

                });

        //订阅调度器上线事件,进行自动负载均衡
        registry.handleSchedulerJoin()
                .subscribe(scheduler -> {

                });
    }

    @Override
    public Mono<Void> reBalance(List<Scheduler> schedulers, boolean balanceAll) {
        if (CollectionUtils.isEmpty(schedulers)) {
            return Mono.empty();
        }
        List<String> schedulerList = schedulers.stream()
                .map(Scheduler::getId)
                .collect(Collectors.toList());

        //查询由其他调度器调度的任务
        snapshotRepository
                .findBySchedulerIdNotIn(schedulerList);


        return null;
    }
}
