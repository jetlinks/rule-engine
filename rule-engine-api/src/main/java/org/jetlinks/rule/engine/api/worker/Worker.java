package org.jetlinks.rule.engine.api.worker;

import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.Task;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 工作器,通常是一个服务器节点
 *
 * @author zhouhao
 * @see 1.0.4
 */
public interface Worker {

    /**
     * @return 全局唯一ID
     */
    String getId();

    /**
     * @return 名称
     */
    String getName();

    /**
     * 创建一个执行器
     *
     * @param job 任务
     * @return 任务执行器
     */
    Mono<Task> createTask(String schedulerId, ScheduleJob job);

    /**
     * @return 支持的执行器ID
     */
    Mono<List<String>> getSupportExecutors();

    /**
     * @return 状态
     */
    Mono<State> getState();

    enum State {
        working,
        shutdown,
        unknown;
    }
}
