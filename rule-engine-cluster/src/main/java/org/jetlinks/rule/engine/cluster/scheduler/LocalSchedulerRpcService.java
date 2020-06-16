package org.jetlinks.rule.engine.cluster.scheduler;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.rpc.RpcServiceFactory;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.cluster.task.TaskRpc;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class LocalSchedulerRpcService implements SchedulerRpcService {

    private final Scheduler localScheduler;

    private final static Map<TaskRpc.TaskOperation, Function<Task, Mono<Void>>> operationMapping = new HashMap<>();

    private final Disposable disposable;

    static {
        operationMapping.put(TaskRpc.TaskOperation.PAUSE, Task::pause);
        operationMapping.put(TaskRpc.TaskOperation.START, Task::start);
        operationMapping.put(TaskRpc.TaskOperation.SHUTDOWN, Task::shutdown);
        operationMapping.put(TaskRpc.TaskOperation.RELOAD, Task::reload);
        operationMapping.put(TaskRpc.TaskOperation.ENABLE_DEBUG, task -> task.debug(true));
        operationMapping.put(TaskRpc.TaskOperation.DISABLE_DEBUG, task -> task.debug(false));
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
    public Flux<SchedulerRpc.WorkerInfo> getWorkers() {
        return localScheduler.getWorkers()
                .map(worker -> new SchedulerRpc.WorkerInfo(worker.getId(), worker.getName()));
    }

    @Override
    public Mono<SchedulerRpc.WorkerInfo> getWorker(String id) {
        return localScheduler.getWorker(id)
                .map(worker -> new SchedulerRpc.WorkerInfo(worker.getId(), worker.getName()));
    }

    @Override
    public Flux<TaskRpc.TaskInfo> schedule(ScheduleJob job) {
        return localScheduler.schedule(job)
                .map(task -> new TaskRpc.TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()));
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        return localScheduler.shutdown(instanceId);
    }

    @Override
    public Flux<TaskRpc.TaskInfo> getSchedulingTask(String instanceId) {
        return localScheduler.getSchedulingTask(instanceId)
                .map(task -> new TaskRpc.TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()));
    }

    @Override
    public Flux<TaskRpc.TaskInfo> getSchedulingTasks() {
        return localScheduler.getSchedulingTasks()
                .map(task -> new TaskRpc.TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()));
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
    public Mono<Void> taskOperation(String taskId, TaskRpc.TaskOperation operation) {

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
    public Mono<TaskRpc.TaskInfo> createTask(String workerId, ScheduleJob job) {
        return localScheduler.getWorker(workerId)
                .flatMap(worker -> worker.createTask(localScheduler.getId(), job))
                .map(task -> new TaskRpc.TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()));
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
}
