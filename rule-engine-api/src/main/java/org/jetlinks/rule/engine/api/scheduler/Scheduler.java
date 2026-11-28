package org.jetlinks.rule.engine.api.scheduler;

import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 任务调度器
 *
 * @author zhouhao
 * @since 1.0.4
 */
public interface Scheduler extends Disposable {

    /**
     * 有界批量运行读取，沿现有实例索引定位，不读取完整任务快照或执行任务。
     * @param instanceIds 最多64个规则身份，调用期间不可修改
     * @return 本节点的完整读取结果；超预算和读取错误明确失败
     * @since 1.3.3
     */
    default Mono<SchedulerRuntimeSnapshot> getRuntime(List<String> instanceIds) {
        return Mono.defer(() -> {
            if (instanceIds == null || instanceIds.size() > SchedulerRuntimeSnapshot.MAX_INSTANCES
                || instanceIds.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 64)) {
                return Mono.error(new IllegalArgumentException("invalid runtime instance scope"));
            }
            return Flux.fromIterable(new LinkedHashSet<>(instanceIds))
                .concatMap(id -> getSchedulingTask(id).concatMap(Task::runtime, 1), 1)
                .take(SchedulerRuntimeSnapshot.MAX_TASKS + 1L)
                .collectList()
                .flatMap(tasks -> tasks.size() > SchedulerRuntimeSnapshot.MAX_TASKS
                    ? Mono.error(new IllegalStateException("runtime task budget exceeded"))
                    : Mono.just(SchedulerRuntimeSnapshot.ready(getId(), tasks)));
        });
    }

    /**
     * @return 调度器ID
     */
    String getId();

    /**
     * @return 全部工作器
     */
    Flux<Worker> getWorkers();

    /**
     * 获取指定ID的工作器
     *
     * @param workerId ID
     * @return 工作器
     */
    Mono<Worker> getWorker(String workerId);

    /**
     * 调度任务并返回执行此任务的执行器,此方法是幂等的,多次调度相同配置的信息,不会创建多个任务。
     *
     * @param job 任务配置
     * @return 返回执行此任务的执行器
     * @see Worker#createTask(String, ScheduleJob)
     */
    Flux<Task> schedule(ScheduleJob job);

    /**
     * 停止任务
     * @param instanceId 实例ID
     * @return void Mono
     */
    Mono<Void> shutdown(String instanceId);


    Mono<Void> shutdownTask(String taskId);

    /**
     * 根据规则ID获取全部调度中的任务
     *
     * @param instanceId 规则ID
     * @return 任务执行信息
     */
    Flux<Task> getSchedulingTask(String instanceId);

    Mono<Task> getTask(String taskId);

    /**
     * 获取全部调度中的任务
     * @return 任务执行信息
     */
    Flux<Task> getSchedulingTasks();

    /**
     * @return 调度中任务总数
     */
    Mono<Long> totalTask();

    /**
     * 当前调度器是否可以调度此任务
     *
     * @param job 任务信息
     * @return 是否可以调度
     */
    Mono<Boolean> canSchedule(ScheduleJob job);

    @Override
    default void dispose(){

    }
}
