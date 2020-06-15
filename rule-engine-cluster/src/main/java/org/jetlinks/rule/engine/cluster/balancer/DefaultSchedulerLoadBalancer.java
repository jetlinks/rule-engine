package org.jetlinks.rule.engine.cluster.balancer;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.cluster.SchedulerRegistry;
import org.jetlinks.rule.engine.cluster.TaskSnapshotRepository;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
public class DefaultSchedulerLoadBalancer implements SchedulerLoadBalancer {

    //均衡器ID,集群全局唯一
    private String id;

    //集群leaderId
    private String leaderId;

    //上线时间
    private long uptime;

    @Setter
    private boolean autoReBalance = true;

    private final EventBus eventBus;

    private final SchedulerRegistry registry;

    private final TaskSnapshotRepository snapshotRepository;

    public DefaultSchedulerLoadBalancer(EventBus eventBus,
                                        SchedulerRegistry registry,
                                        TaskSnapshotRepository snapshotRepository) {
        this.eventBus = eventBus;
        this.registry = registry;
        this.snapshotRepository = snapshotRepository;
    }

    public void setup() {
        uptime = System.currentTimeMillis();
        //恢复当前节点的任务
        Flux.fromIterable(registry.getLocalSchedulers())
                .flatMap(scheduler -> snapshotRepository
                        .findBySchedulerId(scheduler.getId())
                        .filterWhen(snapshot -> scheduler.canSchedule(snapshot.getJob()))
                        .flatMap(snapshot -> scheduler
                                .schedule(snapshot.getJob())
                                .flatMap(task -> {
                                    if (snapshot.getState() == Task.State.running) {
                                        return task.start();
                                    }
                                    return Mono.empty();
                                })
                                .onErrorContinue((err, obj) -> log.debug(err.getMessage(), err)))
                )
                .doOnError(err -> log.debug(err.getMessage(), err))
                .subscribe();

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

    public void cleanup() {

    }

    @Override
    public Mono<Void> reBalance(List<Scheduler> schedulers, boolean balanceAll) {
        if (CollectionUtils.isEmpty(schedulers)) {
            return Mono.empty();
        }
        //TODO 实现re balance
        return Mono.empty();
    }
}
