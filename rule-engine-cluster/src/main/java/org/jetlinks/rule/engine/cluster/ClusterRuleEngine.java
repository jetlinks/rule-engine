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

    //调度器注册中心
    private final SchedulerRegistry schedulerRegistry;

    //任务快照仓库
    private final TaskSnapshotRepository repository;

    //调度器选择器
    private final SchedulerSelector schedulerSelector;

    public ClusterRuleEngine(SchedulerRegistry schedulerRegistry, TaskSnapshotRepository repository) {
        this(schedulerRegistry, repository, SchedulerSelector.selectAll);
    }

    /**
     * 停止规则
     *
     * @param instanceId 实例ID
     * @return void
     */
    @Override
    public Mono<Void> shutdown(String instanceId) {
        //从注册中心中获取调度器来停止指定的规则实例
        return schedulerRegistry
                .getSchedulers()
                .flatMap(scheduler -> scheduler.shutdown(instanceId))
                .then(repository.removeTaskByInstanceId(instanceId))
                .then();
    }


    /**
     * 启动规则
     *
     * @param instanceId 实例ID
     * @param model      规则模型
     * @return 规则实例上下文
     */
    public Flux<Task> startRule(String instanceId, RuleModel model) {
        log.debug("starting rule {}", instanceId);
        //编译
        Map</*nodeId*/String, /*Job*/ScheduleJob> jobs = new ScheduleJobCompiler(instanceId, model)
                .compile()
                .stream()
                .collect(Collectors.toMap(ScheduleJob::getNodeId, Function.identity()));
        List<Task> startedTask = new ArrayList<>(jobs.size());
        //新增调度的任务
        Map</*nodeId*/String, /*Job*/ScheduleJob> newJobs = new LinkedHashMap<>(jobs);

        //获取调度记录
        return repository
                .findByInstanceId(instanceId)
                .doOnNext(snapshot -> newJobs.remove(snapshot.getJob().getNodeId()))
                .flatMap(snapshot -> {
                    ScheduleJob job = jobs.get(snapshot.getJob().getNodeId());
                    //新的规则减少了任务,则尝试移除旧的任务
                    if (job == null) {
                        log.debug("shutdown removed job:{}", snapshot.getJob().getNodeId());
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
                                    .then(repository.saveTaskSnapshots(task.dump()))
                                    .thenReturn(task))
                            //没有worker调度此任务? 重新调度
                            .switchIfEmpty(Mono.fromRunnable(() -> newJobs.put(job.getNodeId(), job)))
                            //调度新增的任务
                            .concatWith(Flux.defer(() -> doStart(newJobs.values())));
                })
                //没有任务调度信息说明可能是新启动的规则
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
                        //将所有Task创建好之后再统一启动
                        .collectList()
                        .flatMapIterable(Function.identity())
                        //统一启动
                        .flatMap(task -> task.start().thenReturn(task)))
                .collectList()
                .map(Flux::fromIterable)
                //保存快照信息
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

    /**
     * 获取运行中的任务
     *
     * @param instance 实例ID
     * @return 规则实例上下文
     */
    @Override
    public Flux<Task> getTasks(String instance) {
        return schedulerRegistry.getSchedulers()
                                .flatMap(scheduler -> scheduler.getSchedulingTask(instance));
    }

    /**
     * 获取全部Worker
     *
     * @return worker
     * @see ScheduleJob#getExecutor()
     */
    @Override
    public Flux<Worker> getWorkers() {
        return schedulerRegistry.getSchedulers()
                                .flatMap(Scheduler::getWorkers);
    }
}
