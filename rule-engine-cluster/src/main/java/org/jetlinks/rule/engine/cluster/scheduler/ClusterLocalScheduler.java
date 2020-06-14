package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.scheduler.SchedulingRule;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.api.worker.WorkerSelector;
import org.jetlinks.rule.engine.cluster.task.TaskRpc;
import org.jetlinks.rule.engine.cluster.worker.ClusterLocalWorker;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
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

    final static WorkerSelector defaultSelector = (workers1, rule) -> workers1.take(1);

    @Setter
    private WorkerSelector workerSelector = defaultSelector;

    private final List<Disposable> disposables = new CopyOnWriteArrayList<>();

    //本地worker
    private final Set<Worker> localWorkers = new ConcurrentSkipListSet<>(Comparator.comparing(Worker::getId));

    //其他节点的worker
    private final Set<Worker> remoteWorkers = new ConcurrentSkipListSet<>(Comparator.comparing(Worker::getId));

    //本地调度中的任务
    private final Map<String/*规则实例ID*/, Map<String/*nodeId*/, List<Task>>> localTasks = new ConcurrentHashMap<>();

    public ClusterLocalScheduler(String id, RpcService rpcService) {
        this.rpcService = rpcService;
        this.id = id;
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
                        (addr, id) -> getSchedulingTask(id)
                                .map(task -> new TaskRpc.TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()))
                )
        );

        disposables.add(
                rpcService.listen(
                        SchedulerRpc.getSchedulingAllJobs(getId()),
                        (addr, id) -> getSchedulingTasks()
                                .map(task -> new TaskRpc.TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()))
                )
        );

    }

    public void cleanup() {
        disposables.forEach(Disposable::dispose);
        disposables.clear();
        for (Worker localWorker : localWorkers) {
            ((ClusterLocalWorker) localWorker).cleanup();
        }
    }

    public void addWorker(Worker worker) {
        localWorkers.add(wrapLocalWorker(worker));
    }

    private Worker wrapLocalWorker(Worker localWorker) {
        ClusterLocalWorker worker = new ClusterLocalWorker(localWorker, rpcService);
        worker.setup();
        return worker;
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
        return findWorker(job.getExecutor(), job)
                .switchIfEmpty(Mono.error(() -> new UnsupportedOperationException("unsupported executor:" + job.getExecutor())))
                .flatMap(worker -> worker.createTask(id, job))
                .doOnNext(task -> getExecutor(job.getInstanceId(), job.getNodeId()).add(task));
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        return getSchedulingTask(instanceId)
                .flatMap(Task::shutdown)
                .then();
    }

    @Override
    public Flux<Task> getSchedulingTask(String instanceId) {
        return Flux.fromIterable(getExecutor(instanceId).values())
                .flatMapIterable(Function.identity());
    }

    @Override
    public Flux<Task> getSchedulingTasks() {
        return Flux.fromIterable(localTasks.values())
                .flatMapIterable(Map::values)
                .flatMapIterable(Function.identity());
    }

    @Override
    public Mono<Long> totalTask() {
        return getSchedulingTasks().count();
    }

    @Override
    public Mono<Boolean> canSchedule(ScheduleJob job) {
        return findWorker(job.getExecutor(), job)
                .hasElements();
    }

    protected Flux<Worker> findWorker(String executor, ScheduleJob job) {
        return workerSelector
                .select(Flux.fromIterable(localWorkers)
                        .filterWhen(exe -> exe.getSupportExecutors()
                                .map(list -> list.contains(executor))
                                .defaultIfEmpty(false)), job);
    }

    private List<Task> getExecutor(String instanceId, String nodeId) {
        return getExecutor(instanceId).computeIfAbsent(nodeId, ignore -> new CopyOnWriteArrayList<>());
    }

    private Map<String, List<Task>> getExecutor(String instanceId) {
        return localTasks.computeIfAbsent(instanceId, ignore -> new ConcurrentHashMap<>());
    }


}
