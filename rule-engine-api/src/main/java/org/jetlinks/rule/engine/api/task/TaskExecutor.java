package org.jetlinks.rule.engine.api.task;

import org.jetlinks.core.metadata.FunctionMetadata;
import reactor.core.publisher.Mono;

import java.util.function.BiConsumer;

/**
 * 任务执行器,本地具体执行任务的地方
 *
 * @author zhouhao
 * @version 1.0.4
 */
public interface TaskExecutor {

    /**
     * @return 执行器名称
     */
    String getName();

    /**
     * 启动
     */
    void start();

    /**
     * 重新加载
     */
    void reload();

    /**
     * 暂停
     */
    void pause();

    /**
     * 停止
     */
    void shutdown();

    /**
     * @return 当前状态
     */
    Task.State getState();

    /**
     * @param listener 状态变更监听器
     */
    void onStateChanged(BiConsumer<Task.State, Task.State> listener);

    /**
     * 验证任务是否可执行,如果未抛出异常则表示一切正常
     *
     * @see IllegalArgumentException
     */
    void validate();

    /**
     * 创建元数据，用于描述此任务的输入输出信息。
     *
     * @return 元数据
     * @see FunctionMetadata#getInputs()
     * @see FunctionMetadata#getOutput()
     * @since 1.2.3
     */
    default Mono<FunctionMetadata> createMetadata() {
        return Mono.empty();
    }
}
