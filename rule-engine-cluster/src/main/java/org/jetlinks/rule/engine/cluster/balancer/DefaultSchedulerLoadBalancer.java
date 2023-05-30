package org.jetlinks.rule.engine.cluster.balancer;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.scheduler.SchedulerSelector;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.cluster.RuleInstance;
import org.jetlinks.rule.engine.cluster.SchedulerRegistry;
import org.jetlinks.rule.engine.cluster.TaskSnapshotRepository;
import org.jetlinks.rule.engine.defaults.ScheduleJobCompiler;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class DefaultSchedulerLoadBalancer implements SchedulerLoadBalancer {

    // TODO: 2021/7/30 自动负载均衡实现
    @Setter
    private boolean autoReBalance = true;

    protected final EventBus eventBus;

    protected final SchedulerRegistry registry;

    protected final TaskSnapshotRepository snapshotRepository;

    protected final SchedulerSelector schedulerSelector;

    public DefaultSchedulerLoadBalancer(EventBus eventBus,
                                        SchedulerRegistry registry,
                                        TaskSnapshotRepository snapshotRepository) {
        this(eventBus, registry, snapshotRepository, SchedulerSelector.selectAll);
    }

    public DefaultSchedulerLoadBalancer(EventBus eventBus,
                                        SchedulerRegistry registry,
                                        TaskSnapshotRepository snapshotRepository,
                                        SchedulerSelector schedulerSelector) {
        this.eventBus = eventBus;
        this.registry = registry;
        this.snapshotRepository = snapshotRepository;
        this.schedulerSelector = schedulerSelector;
    }

    public void setup() {
        setupAsync()
                .block(Duration.ofSeconds(30));
    }

    public Mono<Void> setupAsync() {
        //恢复当前节点的任务
        return Flux
                .fromIterable(registry.getLocalSchedulers())
                .flatMap(scheduler -> snapshotRepository
                        .findBySchedulerId(scheduler.getId())
                        .filterWhen(snapshot -> this
                                .canSchedule(scheduler, snapshot.getJob())
                                .flatMap(can -> {
                                    if (!can) {
                                        return snapshotRepository
                                                .removeTaskById(snapshot.getId())
                                                .thenReturn(false);
                                    }
                                    return Mono.just(true);
                                })
                        )
                        .flatMap(snapshot -> scheduler
                                .schedule(snapshot.getJob())
                                .flatMap(task -> {
                                    if (snapshot.getState() == Task.State.running) {
                                        return task.start();
                                    }
                                    return Mono.empty();
                                })
                                .onErrorResume((err) -> {
                                    log.debug(err.getMessage(), err);
                                    return Mono.empty();
                                }))
                )
                .doOnError(err -> log.debug(err.getMessage(), err))
                .then()
                ;

    }

    public void cleanup() {

    }


    @Override
    public Mono<Void> reBalance(List<Scheduler> schedulers,
                                boolean balanceAll) {
        if (CollectionUtils.isEmpty(schedulers)) {
            return Mono.empty();
        }

        //TODO 实现re balance
        return Mono.error(new UnsupportedOperationException());
    }

    @Override
    public Mono<Void> reBalance(List<Scheduler> schedulers,
                                RuleInstance instance,
                                boolean balanceAll) {
        //编译任务
        Map</*nodeId*/String, /*Job*/ScheduleJob> jobs = new ScheduleJobCompiler(instance.getId(), instance.getModel())
                .compile()
                .stream()
                .collect(Collectors.toMap(ScheduleJob::getNodeId, Function.identity()));

        Map<Scheduler, Map<String, ScheduleJob>> ready = new ConcurrentHashMap<>();
        for (Scheduler scheduler : schedulers) {
            ready.put(scheduler, new ConcurrentHashMap<>(jobs));
        }

        return Flux
                .fromIterable(schedulers)
                .flatMap(scheduler -> {
                    Map<String, ScheduleJob> readyJob = ready.get(scheduler);
                    return scheduler
                            .getSchedulingTask(instance.getId())
                            //已经调度了相同的任务则忽略
                            .doOnNext(task -> readyJob.remove(task.getJob().getNodeId()))
                            .thenMany(
                                    Flux.defer(() -> createTask(scheduler, readyJob.values()))
                            );
                })
                //全部任务都创建成功才进行下一步
                .collectList()
                .flatMap(list -> Flux
                        .fromIterable(list)
                        .flatMap(task -> {
                            log.info("schedule new task[id={} instanceId={} ,nodeId={},executor={}] in scheduler[{}]",
                                     task.getId(),
                                     task.getJob().getInstanceId(),
                                     task.getJob().getNodeId(),
                                     task.getJob().getExecutor(),
                                     task.getSchedulerId());
                            return task.start().thenReturn(task);
                        })
                        .flatMap(Task::dump)
                        .as(snapshotRepository::saveTaskSnapshots)
                );
    }

    private Flux<Task> createTask(Scheduler scheduler, Collection<ScheduleJob> jobs) {
        return Flux
                .fromIterable(jobs)
                .filterWhen(job -> canSchedule(scheduler, job))
                .flatMap(scheduler::schedule);
    }

    protected Mono<Boolean> canSchedule(Scheduler scheduler, ScheduleJob job) {
        return Flux
                .merge(scheduler.canSchedule(job),
                       schedulerSelector.test(scheduler, job))
                .defaultIfEmpty(false)
                .all(Boolean::booleanValue)
                ;
    }
}
