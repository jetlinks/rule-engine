package org.jetlinks.rule.engine.cluster.scheduler;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.cluster.task.TaskRpc;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SchedulerRpcService {

    Flux<SchedulerRpc.WorkerInfo> getWorkers();

    Mono<SchedulerRpc.WorkerInfo> getWorker(String id);

    Flux<TaskRpc.TaskInfo> schedule(ScheduleJob job);

    Mono<Void> shutdown(String instanceId);

    Flux<TaskRpc.TaskInfo> getSchedulingTask(String instanceId);

    Flux<TaskRpc.TaskInfo> getSchedulingTasks();

    Mono<Long> totalTask();

    Mono<Boolean> canSchedule(ScheduleJob job);


    Mono<Void> executeTask(String taskId, RuleData data);

    Mono<Task.State> getTaskState(String taskId);

    Mono<Void> taskOperation(String taskId, TaskRpc.TaskOperation operation);

    Mono<Void> setTaskJob(String taskId, ScheduleJob job);

    Mono<Long> getLastStateTime(String taskId);

    Mono<Long> getStartTime(String taskId);

    Mono<TaskRpc.TaskInfo> createTask(String workerId, ScheduleJob job);

    Mono<List<String>> getSupportExecutors(String workerId);

    Mono<Worker.State> getWorkerState(String workerId);
}
