package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.RuleData;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * 数据输出接口,用于在数据处理完成之后输出结果
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface Output {

    /**
     * 输出规则数据
     *
     * @param data 规则数据
     */
    Mono<Boolean> write(Publisher<RuleData> data);

    Mono<Void> write(String nodeId, Publisher<RuleData> data);
}
