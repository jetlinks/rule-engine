package org.jetlinks.rule.engine.api;

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
    Mono<Task> createTask(ScheduleJob job);

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
        timeout;
    }
}
