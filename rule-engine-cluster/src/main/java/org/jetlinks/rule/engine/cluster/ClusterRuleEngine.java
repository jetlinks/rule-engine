package org.jetlinks.rule.engine.cluster;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.Scheduler;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.TaskSnapshot;
import org.jetlinks.rule.engine.api.ScheduleJob;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.defaults.ScheduleJobCompiler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 集群规则引擎,用于管理,调度规则.
 *
 * @author zhouhao
 */
@AllArgsConstructor
public class ClusterRuleEngine {

    private final SchedulerRegistry schedulerRegistry;

    private final TaskSnapshotRepository repository;

    public Flux<Task> start(String instanceId, RuleModel model) {
        //编译
        Map<String, ScheduleJob> jobs = new ScheduleJobCompiler(instanceId, model).compile()
                .stream()
                .collect(Collectors.toMap(ScheduleJob::getNodeId, Function.identity()));

        //获取调度记录
        return repository
                .findByInstanceId(instanceId)
                .flatMap(snapshot -> this
                        .getTaskBySnapshot(snapshot)
                        .flatMap(task -> task //重新加载任务
                                .setJob(jobs.get(task.getJob().getNodeId()))
                                .then(task.reload())
                                .thenReturn(task)
                        ).switchIfEmpty(Flux.defer(() -> //没有worker调度此任务? 重新调度
                                scheduleTask(jobs.get(snapshot.getJob().getNodeId()))
                                        .flatMap(task -> repository
                                                .saveTaskSnapshots(task.dump())
                                                .thenReturn(task))))
                )
                .switchIfEmpty(doStart(jobs.values()));
    }

    protected Flux<Task> doStart(Collection<ScheduleJob> jobs) {
        return Flux.defer(() -> Flux.fromIterable(jobs)
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
                .flatMap(scheduler -> scheduler.getSchedulingJob(snapshot.getInstanceId()))
                .filter(task -> task.isSameTask(snapshot));
    }

    private Flux<Task> scheduleTask(ScheduleJob job) {
        return schedulerRegistry
                .getSchedulers()
                .filterWhen(scheduler -> scheduler.canSchedule(job))
                .flatMap(scheduler -> Mono.just(scheduler).zipWhen(Scheduler::totalTask))
                .sort(Comparator.comparing(Tuple2::getT2))
                .take(1)
                .flatMap(tp2 -> tp2.getT1().schedule(job));
    }


}
