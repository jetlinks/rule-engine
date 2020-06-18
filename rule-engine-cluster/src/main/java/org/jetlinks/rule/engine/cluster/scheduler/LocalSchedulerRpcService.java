package org.jetlinks.rule.engine.cluster.scheduler;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.rpc.RpcServiceFactory;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskSnapshot;
import org.jetlinks.rule.engine.api.worker.Worker;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class LocalSchedulerRpcService implements SchedulerRpcService {

    private final Scheduler localScheduler;

    private final static Map<TaskOperation, Function<Task, Mono<Void>>> operationMapping = new HashMap<>();

    private final Disposable disposable;

    static {
        operationMapping.put(TaskOperation.PAUSE, Task::pause);
        operationMapping.put(TaskOperation.START, Task::start);
        operationMapping.put(TaskOperation.SHUTDOWN, Task::shutdown);
        operationMapping.put(TaskOperation.RELOAD, Task::reload);
        operationMapping.put(TaskOperation.ENABLE_DEBUG, task -> task.debug(true));
        operationMapping.put(TaskOperation.DISABLE_DEBUG, task -> task.debug(false));
    }

    public LocalSchedulerRpcService(Scheduler localScheduler, RpcServiceFactory serviceFactory) {
        this.localScheduler = localScheduler;
        disposable = serviceFactory.createConsumer("/rule-engine/cluster-scheduler:" + localScheduler.getId(), SchedulerRpcService.class, this);
    }

    public void shutdown() {
        disposable.dispose();
    }

    public Scheduler getLocalScheduler() {
        return localScheduler;
    }

    @Override
    public Flux<WorkerInfo> getWorkers() {
        return localScheduler.getWorkers()
                .map(worker -> new WorkerInfo(worker.getId(), worker.getName()));
    }

    @Override
    public Mono<WorkerInfo> getWorker(String id) {
        return localScheduler.getWorker(id)
                .map(worker -> new WorkerInfo(worker.getId(), worker.getName()));
    }

    @Override
    public Flux<TaskInfo> schedule(ScheduleJob job) {
        return localScheduler.schedule(job)
                .map(task -> new TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()));
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        return localScheduler.shutdown(instanceId);
    }

    @Override
    public Flux<TaskInfo> getSchedulingTask(String instanceId) {
        return localScheduler.getSchedulingTask(instanceId)
                .map(task -> new TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()));
    }

    @Override
    public Flux<TaskInfo> getSchedulingTasks() {
        return localScheduler.getSchedulingTasks()
                .map(task -> new TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()));
    }

    @Override
    public Mono<Long> totalTask() {
        return localScheduler.totalTask();
    }

    @Override
    public Mono<Boolean> canSchedule(ScheduleJob job) {
        return localScheduler.canSchedule(job);
    }

    @Override
    public Mono<Void> executeTask(String taskId, RuleData data) {
        return getTask(taskId)
                .flatMap(task -> task.execute(data))
                .then();
    }

    private Mono<Task> getTask(String taskId) {
        return localScheduler.getSchedulingTasks()
                .filter(task -> task.getId().equals(taskId))
                .singleOrEmpty();
    }

    @Override
    public Mono<Task.State> getTaskState(String taskId) {
        return getTask(taskId)
                .flatMap(Task::getState);
    }

    @Override
    public Mono<Void> taskOperation(String taskId, TaskOperation operation) {

        return getTask(taskId)
                .flatMap(task -> operationMapping.get(operation).apply(task));
    }

    @Override
    public Mono<Void> setTaskJob(String taskId, ScheduleJob job) {
        return getTask(taskId)
                .flatMap(task -> task.setJob(job));
    }

    @Override
    public Mono<Long> getLastStateTime(String taskId) {
        return getTask(taskId)
                .flatMap(Task::getLastStateTime);
    }

    @Override
    public Mono<Long> getStartTime(String taskId) {
        return getTask(taskId)
                .flatMap(Task::getStartTime);
    }

    @Override
    public Mono<TaskInfo> createTask(String workerId, ScheduleJob job) {
        return localScheduler.getWorker(workerId)
                .flatMap(worker -> worker.createTask(localScheduler.getId(), job))
                .map(task -> new TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()));
    }

    @Override
    public Mono<List<String>> getSupportExecutors(String workerId) {
        return localScheduler.getWorker(workerId)
                .flatMap(Worker::getSupportExecutors);
    }

    @Override
    public Mono<Worker.State> getWorkerState(String workerId) {
        return localScheduler.getWorker(workerId)
                .flatMap(Worker::getState);
    }

    @Override
    public Mono<Boolean> isAlive() {
        return Mono.just(true);
    }

    @Override
    public Mono<TaskSnapshot> dumpTask(String taskId) {
        return getTask(taskId)
                .flatMap(Task::dump);
    }
}
