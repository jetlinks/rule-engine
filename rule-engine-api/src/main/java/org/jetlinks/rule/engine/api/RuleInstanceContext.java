package org.jetlinks.rule.engine.api;


import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 规则实例上下文,一个上下文对应一个运行中的规则
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleInstanceContext {

    /**
     * @return 实例ID
     */
    String getId();

    /**
     *
     * @return 当前状态
     */
    RuleInstanceState getState();

    /**
     * 将数据放入规则中执行，并尝试同步返回结果，当获取超时{@link CompletableFuture#get()}会null
     *
     * @param data 数据
     * @return 执行结果
     * @see RuleData#create(Object)
     * @see CompletionStage
     * @see java.util.concurrent.CompletableFuture
     */
    Flux<RuleData> execute(Publisher<RuleData> data);

    /**
     * 启动规则
     */
    Mono<Void> start();

    /**
     * 停止规则
     */
    Mono<Void> stop();

}
