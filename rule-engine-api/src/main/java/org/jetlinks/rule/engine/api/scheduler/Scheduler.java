package org.jetlinks.rule.engine.api.scheduler;

import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 任务调度器
 *
 * @author zhouhao
 * @since 1.0.4
 */
public interface Scheduler extends Disposable {

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

    /**
     * 根据规则ID获取全部调度中的任务
     *
     * @param instanceId 规则ID
     * @return 任务执行信息
     */
    Flux<Task> getSchedulingTask(String instanceId);

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
