package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskSnapshot;
import org.jetlinks.rule.engine.api.worker.Worker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SchedulerRpcService {

    Flux<WorkerInfo> getWorkers();

    Mono<WorkerInfo> getWorker(String id);

    Flux<TaskInfo> schedule(ScheduleJob job);

    Mono<Void> shutdown(String instanceId);

    Flux<TaskInfo> getSchedulingTask(String instanceId);

    Flux<TaskInfo> getSchedulingTasks();

    Mono<Long> totalTask();

    Mono<Boolean> canSchedule(ScheduleJob job);

    Mono<Void> executeTask(String taskId, RuleData data);

    Mono<Task.State> getTaskState(String taskId);

    Mono<Void> taskOperation(String taskId, TaskOperation operation);

    Mono<Void> setTaskJob(String taskId, ScheduleJob job);

    Mono<Long> getLastStateTime(String taskId);

    Mono<Long> getStartTime(String taskId);

    Mono<TaskInfo> createTask(String workerId, ScheduleJob job);

    Mono<List<String>> getSupportExecutors(String workerId);

    Mono<Worker.State> getWorkerState(String workerId);

    Mono<Boolean> isAlive();

    Mono<TaskSnapshot> dumpTask(String taskId);

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    class WorkerInfo {
        private String id;

        private String name;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    class TaskInfo {
        private String id;

        private String name;

        private String workerId;

        private ScheduleJob job;

    }

    enum TaskOperation {
        START,
        PAUSE,
        RELOAD,
        SHUTDOWN,
        ENABLE_DEBUG,
        DISABLE_DEBUG
    }
}
