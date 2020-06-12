package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.Scheduler;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.Worker;
import org.jetlinks.rule.engine.api.WorkerSelector;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import org.jetlinks.rule.engine.api.ScheduleJob;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.cluster.TaskSnapshotRepository;
import org.jetlinks.rule.engine.cluster.task.TaskRpc;
import org.jetlinks.rule.engine.cluster.worker.ClusterLocalWorker;
import reactor.core.Disposable;
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

public class ClusterLocalScheduler implements Scheduler {

    @Getter
    private final String id;
    private final RpcService rpcService;
    private final TaskSnapshotRepository repository;

    @Setter
    private WorkerSelector workerSelector;

    private final EmitterProcessor<Worker> joinProcessor = EmitterProcessor.create(false);
    private final EmitterProcessor<Worker> leaveProcessor = EmitterProcessor.create(false);
    private final FluxSink<Worker> joinSink = joinProcessor.sink(FluxSink.OverflowStrategy.BUFFER);
    private final FluxSink<Worker> leaveSink = leaveProcessor.sink(FluxSink.OverflowStrategy.BUFFER);
    private final List<Disposable> disposables = new CopyOnWriteArrayList<>();

    //本地worker
    private final Set<Worker> localWorkers = new ConcurrentSkipListSet<>(Comparator.comparing(Worker::getId));

    //其他节点的worker
    private final Set<Worker> remoteWorkers = new ConcurrentSkipListSet<>(Comparator.comparing(Worker::getId));

    //本地调度中的任务
    private final Map<String/*规则实例ID*/, Map<String/*nodeId*/, List<Task>>> localTasks = new ConcurrentHashMap<>();

    public ClusterLocalScheduler(String id, RpcService rpcService, TaskSnapshotRepository repository) {
        this.rpcService = rpcService;
        this.id = id;
        this.repository = repository;
    }

    public void setup() {

        disposables.add(
                rpcService.listen(
                        SchedulerRpc.canSchedule(getId()),
                        (addr, job) -> canSchedule(job)
                )
        );

        disposables.add(
                rpcService.listen(
                        SchedulerRpc.getTotalTasks(getId()),
                        (addr) -> totalTask()
                )
        );

        disposables.add(
                rpcService.listen(
                        SchedulerRpc.getWorkers(getId()),
                        (addr) -> getWorkers()
                                .map(worker -> new SchedulerRpc.WorkerInfo(worker.getId(), worker.getName()))
                )
        );

        disposables.add(
                rpcService.listen(
                        SchedulerRpc.schedule(getId()),
                        (addr, job) -> schedule(job)
                                .map(task -> new TaskRpc.TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()))
                )
        );

        disposables.add(
                rpcService.listen(
                        SchedulerRpc.shutdown(getId()),
                        (addr, id) -> shutdown(id)
                )
        );

        disposables.add(
                rpcService.listen(
                        SchedulerRpc.getSchedulingJobs(getId()),
                        (addr, id) -> getSchedulingJob(id)
                                .map(task -> new TaskRpc.TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()))
                )
        );

        disposables.add(
                rpcService.listen(
                        SchedulerRpc.getSchedulingAllJobs(getId()),
                        (addr, id) -> getSchedulingJobs()
                                .map(task -> new TaskRpc.TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()))
                )
        );

    }

    public void cleanup() {
        disposables.forEach(Disposable::dispose);
        disposables.clear();
        localWorkers.forEach(leaveSink::next);
        for (Worker localWorker : localWorkers) {
            ((ClusterLocalWorker) localWorker).cleanup();
        }
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
        ClusterLocalWorker worker = new ClusterLocalWorker(localWorker, rpcService);
        worker.setup();
        return worker;
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
        //判断调度中的任务
        List<Task> tasks = getExecutor(job.getInstanceId(), job.getNodeId());
        if (tasks.isEmpty()) {
            return createExecutor(job);
        }
        return Flux
                .fromIterable(tasks)
                .flatMap(task -> task.setJob(job)
                        .then(task.reload())
                        .thenReturn(task));
    }

    private Flux<Task> createExecutor(ScheduleJob job) {
        return findWorker(job.getExecutor(), job.getSchedulingRule())
                .switchIfEmpty(Mono.error(() -> new UnsupportedOperationException("unsupported executor:" + job.getExecutor())))
                .flatMap(worker -> worker.createTask(job))
                .doOnNext(task -> getExecutor(job.getInstanceId(), job.getNodeId()).add(task));
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        return getSchedulingJob(instanceId)
                .flatMap(Task::shutdown)
                .then();
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
        return findWorker(job.getExecutor(), job.getSchedulingRule())
                .hasElements();
    }

    protected Flux<Worker> findWorker(String executor, SchedulingRule schedulingRule) {
        return workerSelector
                .select(Flux.fromIterable(localWorkers)
                        .filterWhen(exe -> exe.getSupportExecutors()
                                .map(list -> list.contains(executor))
                                .defaultIfEmpty(false)), schedulingRule);
    }

    private List<Task> getExecutor(String instanceId, String nodeId) {
        return getExecutor(instanceId).computeIfAbsent(nodeId, ignore -> new CopyOnWriteArrayList<>());
    }

    private Map<String, List<Task>> getExecutor(String instanceId) {
        return localTasks.computeIfAbsent(instanceId, ignore -> new ConcurrentHashMap<>());
    }


}
