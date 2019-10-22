package org.jetlinks.rule.engine.api.executor;

import org.jetlinks.rule.engine.api.RuleData;
import reactor.core.publisher.Flux;

/**
 * 数据输入接口
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface Input {

    Flux<RuleData> subscribe();

    /**
     * 关闭,停止消费数据
     */
    void close();
}
