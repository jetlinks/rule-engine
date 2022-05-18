package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.api.worker.WorkerSelector;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class LocalScheduler implements Scheduler {

    @Getter
    private final String id;

    @Setter
    private WorkerSelector workerSelector = defaultSelector;

    final static WorkerSelector defaultSelector = (workers1, rule) -> workers1.take(1);

    private final Map<String/*workerId*/, Worker> workers = new ConcurrentHashMap<>();

    private final Map<String/*规则实例ID*/, Map<String/*nodeId*/, List<Task>>> executors = new ConcurrentHashMap<>();

    public LocalScheduler(String id) {
        this.id = id;
    }

    @Override
    public Flux<Worker> getWorkers() {
        return Flux.fromIterable(workers.values());
    }

    @Override
    public Mono<Worker> getWorker(String workerId) {

        return Mono.justOrEmpty(workers.get(workerId));
    }

    @Override
    public Mono<Boolean> canSchedule(ScheduleJob job) {
        return findWorker(job.getExecutor(), job)
                .hasElements();
    }

    protected Flux<Worker> findWorker(String executor, ScheduleJob schedulingRule) {
        return workerSelector
                .select(Flux.fromIterable(workers.values())
                            .filterWhen(exe -> exe
                                    .getSupportExecutors()
                                    .map(list -> list.contains(executor))
                                    .defaultIfEmpty(false)), schedulingRule);
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
                .flatMap(task -> task
                        .setJob(job)
                        .then(task.reload())
                        .thenReturn(task));
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        return getSchedulingTask(instanceId)
                .flatMap(Task::shutdown)
                .then(Mono.fromRunnable(() -> clearExecutor(instanceId)));
    }

    private Flux<Task> createExecutor(ScheduleJob job) {
        return findWorker(job.getExecutor(), job)
                .switchIfEmpty(Mono.error(() -> new UnsupportedOperationException("unsupported executor:" + job.getExecutor())))
                .flatMap(worker -> worker.createTask(id, job))
                .doOnNext(task -> getExecutor(job.getInstanceId(), job.getNodeId()).add(task));
    }

    @Override
    public Flux<Task> getSchedulingTask(String instanceId) {
        return Flux.fromIterable(getExecutor(instanceId).values())
                   .flatMapIterable(Function.identity());
    }

    @Override
    public Flux<Task> getSchedulingTasks() {
        return Flux.fromIterable(executors.values())
                   .flatMapIterable(Map::values)
                   .flatMapIterable(Function.identity());
    }

    @Override
    public Mono<Long> totalTask() {
        return getSchedulingTasks().count();
    }

    private List<Task> getExecutor(String instanceId, String nodeId) {
        return getExecutor(instanceId).computeIfAbsent(nodeId, ignore -> new CopyOnWriteArrayList<>());
    }

    private void clearExecutor(String instanceId) {
        executors.remove(instanceId);
    }

    private Map<String, List<Task>> getExecutor(String instanceId) {
        return executors.computeIfAbsent(instanceId, ignore -> new ConcurrentHashMap<>());
    }

    public void addWorker(Worker worker) {
        this.workers.put(worker.getId(), worker);
    }
}
