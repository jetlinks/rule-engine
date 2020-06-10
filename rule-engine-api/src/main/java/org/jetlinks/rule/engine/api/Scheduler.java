package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.executor.ScheduleJob;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

/**
 * 任务调度器
 *
 * @author zhouhao
 * @since 1.0.4
 */
public interface Scheduler {

    /**
     * @return 调度器ID
     */
    String getId();

    /**
     * @return 全部工作器
     */
    List<Worker> getWorkers();

    /**
     * 获取指定ID的工作器
     *
     * @param workerId ID
     * @return 工作器
     */
    Optional<Worker> getWorker(String workerId);

    /**
     * 调度任务并返回执行此任务的执行器,此方法是幂等的,多次调度相同配置的信息,不会创建多个任务。
     *
     * @param job 任务配置
     * @return 返回执行此任务的执行器
     * @see Worker#createExecutor(ScheduleJob)
     */
    Flux<Task> schedule(ScheduleJob job);

    /**
     * 根据规则ID获取全部调度中的任务
     *
     * @param instanceId 规则ID
     * @return 任务执行信息
     */
    Flux<Task> getSchedulingJob(String instanceId);

}
