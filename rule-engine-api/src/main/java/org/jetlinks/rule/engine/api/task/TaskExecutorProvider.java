package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import reactor.core.publisher.Mono;

/**
 * 任务执行器提供商,用于根据上下文创建执行器.
 *
 * @author zhouhao
 * @since 1.1.0
 */
public interface TaskExecutorProvider {

    /**
     * 执行器标识 {@link RuleNodeModel#getExecutor()}
     *
     * @return 执行器标识
     */
    String getExecutor();

    /**
     * 创建执行任务
     *
     * @param context 上下文
     * @return 任务
     */
    Mono<TaskExecutor> createTask(ExecutionContext context);

}
