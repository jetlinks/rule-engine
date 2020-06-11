package org.jetlinks.rule.engine.cluster;

import lombok.Getter;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.Worker;
import org.jetlinks.rule.engine.api.executor.ScheduleJob;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 本地集群调度器
 * 1. 注册worker时,自动恢复任务
 */
public class LocalClusterScheduler implements ClusterScheduler {

    @Getter
    private String id;

    private TaskSnapshotRepository repository;

    private final EmitterProcessor<Worker> joinProcessor = EmitterProcessor.create(false);
    private final EmitterProcessor<Worker> leaveProcessor = EmitterProcessor.create(false);
    private final FluxSink<Worker> joinSink = joinProcessor.sink(FluxSink.OverflowStrategy.BUFFER);
    private final FluxSink<Worker> leaveSink = leaveProcessor.sink(FluxSink.OverflowStrategy.BUFFER);

    //本地worker
    private final Set<Worker> localWorkers = new ConcurrentSkipListSet<>(Comparator.comparing(Worker::getId));

    //其他节点的worker
    private final Set<Worker> remoteWorkers = new ConcurrentSkipListSet<>(Comparator.comparing(Worker::getId));

    //本地调度中的任务
    private final Map<String/*规则实例ID*/, Map<String/*nodeId*/, List<Task>>> localTasks = new ConcurrentHashMap<>();

    @Override
    public Flux<Worker> handleWorkerJoin() {
        return joinProcessor;
    }

    @Override
    public Flux<Worker> handleWorkerLeave() {
        return leaveProcessor;
    }

    public void shutdown() {
        localWorkers.forEach(leaveSink::next);
    }

    public Mono<Void> register(Worker worker) {
        return Mono.defer(() -> {
            Worker wrap = wrapLocalWorker(worker);
            localWorkers.add(wrap);
            joinSink.next(worker);
            //恢复任务
            return reScheduleLocalWorkerJobs(wrap);
        });
    }

    private Worker wrapLocalWorker(Worker localWorker) {
        //todo 包装为LocalClusterWorker

        return localWorker;
    }

    //重新调度本节点的任务
    private Mono<Void> reScheduleLocalWorkerJobs(Worker worker) {
        return repository.findByWorkerId(worker.getId())
                .flatMap(snapshot -> worker.createTask(snapshot.getJob()))
                .doOnNext(task -> getExecutor(task.getJob().getInstanceId(), task.getJob().getNodeId()).add(task))
                .flatMap(Task::start)
                .then();
    }

    @Override
    public Flux<Worker> getWorkers() {
        return Flux.just(localWorkers, remoteWorkers)
                .flatMapIterable(Function.identity());
    }

    @Override
    public Mono<Worker> getWorker(String workerId) {
        return getWorkers()
                .filter(worker -> worker.getId().equals(workerId))
                .take(1)
                .singleOrEmpty();
    }

    @Override
    public Flux<Task> schedule(ScheduleJob job) {

        return null;
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        return null;
    }

    @Override
    public Flux<Task> getSchedulingJob(String instanceId) {
        return Flux.fromIterable(getExecutor(instanceId).values())
                .flatMapIterable(Function.identity());
    }

    @Override
    public Flux<Task> getSchedulingJobs() {
        return Flux.fromIterable(localTasks.values())
                .flatMapIterable(Map::values)
                .flatMapIterable(Function.identity());
    }

    @Override
    public Mono<Long> totalTask() {
        return getSchedulingJobs().count();
    }


    @Override
    public Mono<Boolean> canSchedule(ScheduleJob job) {
        return null;
    }

    private List<Task> getExecutor(String instanceId, String nodeId) {
        return getExecutor(instanceId).computeIfAbsent(nodeId, ignore -> new CopyOnWriteArrayList<>());
    }

    private Map<String, List<Task>> getExecutor(String instanceId) {
        return localTasks.computeIfAbsent(instanceId, ignore -> new ConcurrentHashMap<>());
    }
}
