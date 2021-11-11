package org.jetlinks.rule.engine.cluster;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.RuleEngine;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.scheduler.SchedulerSelector;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskSnapshot;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.defaults.ScheduleJobCompiler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 集群规则引擎,用于管理,调度规则.
 *
 * @author zhouhao
 */
@AllArgsConstructor
@Slf4j
public class ClusterRuleEngine implements RuleEngine {

    private final SchedulerRegistry schedulerRegistry;

    private final TaskSnapshotRepository repository;

    private final SchedulerSelector schedulerSelector;

    public ClusterRuleEngine(SchedulerRegistry schedulerRegistry, TaskSnapshotRepository repository) {
        this(schedulerRegistry, repository, SchedulerSelector.selectAll);
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        return schedulerRegistry
                .getSchedulers()
                .flatMap(scheduler -> scheduler.shutdown(instanceId))
                .then(repository.removeTaskByInstanceId(instanceId))
                .then();
    }

    public Flux<Task> startRule(String instanceId, RuleModel model) {
        //编译
        Map</*nodeId*/String, /*Job*/ScheduleJob> jobs = new ScheduleJobCompiler(instanceId, model)
                .compile()
                .stream()
                .collect(Collectors.toMap(ScheduleJob::getNodeId, Function.identity()));
        List<Task> startedTask = new ArrayList<>(jobs.size());
        //获取调度记录
        return repository
                .findByInstanceId(instanceId)
                .flatMap(snapshot -> {
                    ScheduleJob job = jobs.get(snapshot.getJob().getNodeId());
                    //新的任务减少了task
                    if (job == null) {
                        return this
                                .getTaskBySnapshot(snapshot)
                                .flatMap(Task::shutdown)
                                .then(repository.removeTaskById(snapshot.getId()))
                                .then(Mono.empty());
                    }
                    return this
                            .getTaskBySnapshot(snapshot)
                            //重新加载任务
                            .flatMap(task -> task
                                    .setJob(job)
                                    .then(task.reload())
                                    .thenReturn(task))
                            //没有worker调度此任务? 重新调度
                            .switchIfEmpty(Flux.defer(() -> this
                                    .doStart(Collections.singleton(job))
                                    .flatMap(task -> repository
                                            .saveTaskSnapshots(task.dump())
                                            .thenReturn(task))));

                })
                .switchIfEmpty(doStart(jobs.values()))
                .doOnNext(startedTask::add)
                .onErrorResume(err -> {
                    //失败时停止全部任务
                    return Flux
                            .fromIterable(startedTask)
                            .flatMap(Task::shutdown)
                            .then(Mono.error(err));
                });
    }

    protected Flux<Task> doStart(Collection<ScheduleJob> jobs) {
        return Flux
                .defer(() -> Flux
                        .fromIterable(jobs)
                        .flatMap(this::scheduleTask)
                        .collectList()
                        .flatMapIterable(Function.identity())
                        .flatMap(task -> task.start().thenReturn(task)))
                .collectList()
                .map(Flux::fromIterable)
                .flatMapMany(tasks -> repository
                        .saveTaskSnapshots(tasks.flatMap(Task::dump))
                        .thenMany(tasks));
    }

    //获取调度中的任务信息
    private Flux<Task> getTaskBySnapshot(TaskSnapshot snapshot) {
        return schedulerRegistry
                .getSchedulers()
                .flatMap(scheduler -> scheduler.getSchedulingTask(snapshot.getInstanceId()))
                .filter(task -> task.isSameTask(snapshot));
    }

    private Flux<Task> scheduleTask(ScheduleJob job) {
        return schedulerRegistry
                .getSchedulers()
                .filterWhen(scheduler -> scheduler.canSchedule(job))
                .as(supports -> schedulerSelector.select(supports, job))
                .switchIfEmpty(Mono.error(() -> new UnsupportedOperationException("no scheduler for " + job.getExecutor())))
                .flatMap(scheduler -> scheduler.schedule(job));
    }

    @Override
    public Flux<Task> getTasks(String instance) {
        return schedulerRegistry.getSchedulers()
                                .flatMap(scheduler -> scheduler.getSchedulingTask(instance));
    }

    @Override
    public Flux<Worker> getWorkers() {
        return schedulerRegistry.getSchedulers()
                                .flatMap(Scheduler::getWorkers);
    }
}
