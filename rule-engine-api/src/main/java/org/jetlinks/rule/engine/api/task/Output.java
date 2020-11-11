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

    default Mono<Boolean> write(RuleData data) {
        return write(Mono.just(data));
    }

    /**
     * 写出数据到指定到节点
     *
     * @param nodeId 节点ID
     * @param data   数据流
     * @return void
     */
    Mono<Void> write(String nodeId, Publisher<RuleData> data);

    default Mono<Void> write(String nodeId, RuleData data) {
        return write(nodeId, Mono.just(data));
    }
}
