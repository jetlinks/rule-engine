package org.jetlinks.rule.engine.cluster;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.rule.engine.api.RuleConstants;
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
import java.util.concurrent.ConcurrentHashMap;
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

    @Override
    public Mono<Void> shutdown(String instanceId) {
        //从注册中心中获取调度器来停止指定的规则实例
        return schedulerRegistry
                .getSchedulers()
                .flatMap(scheduler -> scheduler.shutdown(instanceId))
                .then(repository.removeTaskByInstanceId(instanceId))
                .then();
    }

    private Mono<Void> shutdown(TaskSnapshot snapshot) {
        return schedulerRegistry
                .getSchedulers()
                .filter(scheduler -> Objects.equals(snapshot.getSchedulerId(), scheduler.getId()))
                .flatMap(scheduler -> scheduler.shutdownTask(snapshot.getId()))
                .then(repository.removeTaskById(snapshot.getId()));
    }

    public Flux<Task> startRule(String instanceId, RuleModel model) {
        log.debug("starting rule {}\n{}", instanceId, model.toString());
        //编译
        Map</*nodeId*/String, /*Job*/ScheduleJob> jobs = new ScheduleJobCompiler(instanceId, model)
                .compile()
                .stream()
                .collect(Collectors.toMap(ScheduleJob::getNodeId, Function.identity()));
        List<Task> startedTask = new ArrayList<>(jobs.size());
        //新增调度的任务
        Map</*nodeId*/String, /*Job*/ScheduleJob> readyToStart = new ConcurrentHashMap<>(jobs);

        //获取调度记录
        return repository
                .findByInstanceId(instanceId)
                .doOnNext(snapshot -> readyToStart.remove(snapshot.getJob().getNodeId()))
                .flatMap(snapshot -> {
                    ScheduleJob job = jobs.get(snapshot.getJob().getNodeId());
                    ScheduleJob old = snapshot.getJob();
                    //新的规则减少了任务,则尝试移除旧的任务
                    if (job == null || !Objects.equals(job.getExecutor(), old.getExecutor())) {
                        if (job != null && !Objects.equals(job.getExecutor(), old.getExecutor())) {
                            //移除了旧的,需要重新调度新的
                            readyToStart.put(job.getNodeId(), job);
                            log.debug("change job [{}] executor:{} -> {}", snapshot.getJob().getNodeId(),
                                      snapshot.getJob().getExecutor(), job.getExecutor());
                        } else {
                            log.debug("shutdown removed job:{}", snapshot.getJob().getNodeId());
                        }
                        return this
                                .shutdown(snapshot)
                                .then(Mono.empty());
                    }
                    return this
                            .getTaskBySnapshot(snapshot)
                            .flatMap(task -> {
                                startedTask.add(task);
                                //重新加载任务
                                return task
                                        .setJob(job)
                                        .then(task.reload())
                                        .thenReturn(task);
                            })
                            //没有worker调度此任务? 重新调度
                            .switchIfEmpty(Mono.fromRunnable(() -> readyToStart.put(job.getNodeId(), job)));

                })
                .concatWith(Flux.defer(() -> doStart(readyToStart.values())).doOnNext(startedTask::add))
                .collectList()
                .map(Flux::fromIterable)
                //保存快照信息
                .flatMapMany(tasks -> repository
                        .saveTaskSnapshots(tasks.flatMap(Task::dump))
                        .thenMany(tasks))
                .onErrorResume(err -> {
                    //失败时停止全部任务
                    return Flux
                            .fromIterable(startedTask)
                            .flatMap(Task::shutdown)
                            .then(Mono.error(err));
                })
                .as(FluxTracer.create(RuleConstants.Trace.spanName(instanceId,"start"), builder -> {
                    builder.setAttribute(RuleConstants.Trace.model, model.toString());
                    builder.setAttribute(RuleConstants.Trace.instanceId, instanceId);
                }));
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
                        .flatMap(task -> task.start().thenReturn(task)));
    }

    //获取调度中的任务信息
    private Flux<Task> getTaskBySnapshot(TaskSnapshot snapshot) {
        return schedulerRegistry
                .getSchedulers()
                .flatMap(scheduler -> scheduler.getTask(snapshot.getId()));
    }

    private Flux<Scheduler> selectScheduler(ScheduleJob job) {
        return schedulerRegistry
                .getSchedulers()
                .filterWhen(scheduler -> scheduler.canSchedule(job))
                .as(supports -> schedulerSelector.select(supports, job));
    }

    private Flux<Task> scheduleTask(ScheduleJob job) {
        return selectScheduler(job)
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
