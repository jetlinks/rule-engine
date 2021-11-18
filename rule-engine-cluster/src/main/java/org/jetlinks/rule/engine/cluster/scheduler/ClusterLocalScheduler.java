package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.core.rpc.RpcServiceFactory;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.api.worker.WorkerSelector;
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

@Deprecated
public class ClusterLocalScheduler implements Scheduler {

    @Getter
    private final String id;
    final static WorkerSelector defaultSelector = (workers1, rule) -> workers1.take(1);

    @Setter
    private WorkerSelector workerSelector = defaultSelector;

    private final List<Disposable> disposables = new CopyOnWriteArrayList<>();

    //本地worker
    private final Set<Worker> localWorkers = new ConcurrentSkipListSet<>(Comparator.comparing(Worker::getId));

    //本地调度中的任务
    private final Map<String/*规则实例ID*/, Map<String/*nodeId*/, List<Task>>> localTasks = new ConcurrentHashMap<>();

    private final LocalSchedulerRpcService rpcService;

    public ClusterLocalScheduler(String id, RpcServiceFactory serviceFactory) {
        this.id = id;
        rpcService = new LocalSchedulerRpcService(this, serviceFactory);
    }


    public void cleanup() {
        disposables.forEach(Disposable::dispose);
        disposables.clear();
        rpcService.shutdown();
    }

    public void addWorker(Worker worker) {
        localWorkers.add(wrapLocalWorker(worker));
    }

    private Worker wrapLocalWorker(Worker localWorker) {
        return localWorker;
    }

    @Override
    public Flux<Worker> getWorkers() {
        return Flux.just(localWorkers)
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
        List<Task> tasks = getTasks(job.getInstanceId(), job.getNodeId());
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
                .doOnNext(task -> getTasks(job.getInstanceId(), job.getNodeId()).add(task));
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {

        return getSchedulingTask(instanceId)
                .flatMap(Task::shutdown)
                .then(Mono.fromRunnable(() -> getTasks(instanceId).clear()));
    }

    @Override
    public Flux<Task> getSchedulingTask(String instanceId) {
        return Flux.fromIterable(getTasks(instanceId).values())
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

    private List<Task> getTasks(String instanceId, String nodeId) {
        return getTasks(instanceId).computeIfAbsent(nodeId, ignore -> new CopyOnWriteArrayList<>());
    }

    private Map<String, List<Task>> getTasks(String instanceId) {
        return localTasks.computeIfAbsent(instanceId, ignore -> new ConcurrentHashMap<>());
    }


}
