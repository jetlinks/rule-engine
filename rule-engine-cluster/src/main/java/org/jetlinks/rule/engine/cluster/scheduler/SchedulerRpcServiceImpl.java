package org.jetlinks.rule.engine.cluster.scheduler;

import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskSnapshot;
import org.jetlinks.rule.engine.api.worker.Worker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SchedulerRpcServiceImpl implements SchedulerRpcService {

    private final Scheduler localScheduler;

    private final static Map<TaskOperation, Function<Task, Mono<Void>>> operationMapping = new HashMap<>();

    static {
        operationMapping.put(TaskOperation.PAUSE, Task::pause);
        operationMapping.put(TaskOperation.START, Task::start);
        operationMapping.put(TaskOperation.SHUTDOWN, Task::shutdown);
        operationMapping.put(TaskOperation.RELOAD, Task::reload);
        operationMapping.put(TaskOperation.ENABLE_DEBUG, task -> task.debug(true));
        operationMapping.put(TaskOperation.DISABLE_DEBUG, task -> task.debug(false));
    }

    public SchedulerRpcServiceImpl(Scheduler localScheduler) {
        this.localScheduler = localScheduler;
    }

    public Scheduler getLocalScheduler() {
        return localScheduler;
    }

    @Override
    public Flux<WorkerInfo> getWorkers() {
        return localScheduler
                .getWorkers()
                .map(worker -> new WorkerInfo(worker.getId(), worker.getName()));
    }

    @Override
    public Mono<WorkerInfo> getWorker(String id) {
        return localScheduler
                .getWorker(id)
                .map(worker -> new WorkerInfo(worker.getId(), worker.getName()));
    }

    @Override
    public Flux<TaskInfo> schedule(ScheduleJob job) {
        return localScheduler
                .schedule(job)
                .map(task -> new TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()));
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        return localScheduler.shutdown(instanceId);
    }

    @Override
    public Flux<TaskInfo> getSchedulingTask(String instanceId) {
        return localScheduler
                .getSchedulingTask(instanceId)
                .map(task -> new TaskInfo(task.getId(), task.getName(), task.getWorkerId(), task.getJob()));
    }

    @Override
    public Flux<TaskInfo> getSchedulingTasks() {
        return localScheduler
                .getSchedulingTasks()
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
    public Mono<Void> executeTask(ExecuteTaskRequest request) {
        return getTask(request.getTaskId())
                .flatMap(task -> task.execute(request.getData()))
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
    public Mono<Void> taskOperation(OperateTaskRequest request) {

        return getTask(request.getTaskId())
                .flatMap(task -> operationMapping.get(request.getOperation()).apply(task));
    }

    @Override
    public Mono<Void> setTaskJob(TaskJobRequest request) {
        return getTask(request.getTaskId())
                .flatMap(task -> task.setJob(request.getJob()));
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
    public Mono<TaskInfo> createTask(CreateTaskRequest request) {
        return localScheduler.getWorker(request.getWorkerId())
                             .flatMap(worker -> worker.createTask(localScheduler.getId(), request.getJob()))
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
