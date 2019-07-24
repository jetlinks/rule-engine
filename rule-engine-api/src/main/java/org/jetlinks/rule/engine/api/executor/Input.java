package org.jetlinks.rule.engine.api.executor;

import org.jetlinks.rule.engine.api.RuleData;

import java.util.function.Consumer;

/**
 * 数据输入接口
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface Input {

    /**
     * 设置消费者进行数据消费,同一个Input只能存在一个消费者
     *
     * @param accept 消费者
     * @return 是否设置成功
     */
    boolean accept(Consumer<RuleData> accept);

    /**
     * 关闭,停止消费数据
     */
    void close();
}
