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

public class ClusterLocalScheduler implements Scheduler {

    //调度器ID
    @Getter
    private final String id;

    final static WorkerSelector defaultSelector = (workers1, rule) -> workers1.take(1);

    //工作节点选择器,用来选择合适的节点来执行任务
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

    /**
     * @return 全部工作器
     */
    @Override
    public Flux<Worker> getWorkers() {
        return Flux.just(localWorkers)
                .flatMapIterable(Function.identity());
    }

    /**
     * 获取指定ID的工作器
     *
     * @param workerId ID
     * @return 工作器
     */
    @Override
    public Mono<Worker> getWorker(String workerId) {
        return getWorkers()
                .filter(worker -> worker.getId().equals(workerId))
                .take(1)
                .singleOrEmpty();
    }

    /**
     * 调度任务并返回执行此任务的执行器,此方法是幂等的,多次调度相同配置的信息,不会创建多个任务。
     *
     * @param job 任务配置
     * @return 返回执行此任务的执行器
     * @see Worker#createTask(String, ScheduleJob)
     */
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

    /**
     * 停止任务
     * @param instanceId 实例ID
     * @return void Mono
     */
    @Override
    public Mono<Void> shutdown(String instanceId) {

        return getSchedulingTask(instanceId)
                .flatMap(Task::shutdown)
                .then(Mono.fromRunnable(() -> getTasks(instanceId).clear()));
    }

    /**
     * 根据规则ID获取全部调度中的任务
     *
     * @param instanceId 规则ID
     * @return 任务执行信息
     */
    @Override
    public Flux<Task> getSchedulingTask(String instanceId) {
        return Flux.fromIterable(getTasks(instanceId).values())
                .flatMapIterable(Function.identity());
    }

    /**
     * 获取全部调度中的任务
     * @return 任务执行信息
     */
    @Override
    public Flux<Task> getSchedulingTasks() {
        return Flux.fromIterable(localTasks.values())
                .flatMapIterable(Map::values)
                .flatMapIterable(Function.identity());
    }

    /**
     * @return 调度中任务总数
     */
    @Override
    public Mono<Long> totalTask() {
        return getSchedulingTasks().count();
    }

    /**
     * 当前调度器是否可以调度此任务
     *
     * @param job 任务信息
     * @return 是否可以调度
     */
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

    /**
     * 获取运行任务
     *
     * @param instanceId 规则实例ID
     * @param nodeId 规则节点ID
     * @return 任务集合
     */
    private List<Task> getTasks(String instanceId, String nodeId) {
        return getTasks(instanceId).computeIfAbsent(nodeId, ignore -> new CopyOnWriteArrayList<>());
    }

    /**
     * 获取运行任务
     *
     * @param instanceId 规则实例ID
     * @return 任务集合
     */
    private Map<String, List<Task>> getTasks(String instanceId) {
        return localTasks.computeIfAbsent(instanceId, ignore -> new ConcurrentHashMap<>());
    }


}
