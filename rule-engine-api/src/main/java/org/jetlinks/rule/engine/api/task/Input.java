package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.RuleData;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * 数据输入接口
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface Input {

    /**
     * 监听数据输入
     *
     * @return 数据流
     */
    Flux<RuleData> accept();

    /**
     * 使用指定的监听器监听数据输入,如果监听器返回false或者返回error,表示处理失败.
     * 不同的Input实现可能会对此数据做不同的处理,比如重新入队等操作.
     * 可通过返回值{@link Disposable#dispose()}来结束监听
     *
     * @param listener 监听器
     * @return Disposable
     */
    default Disposable accept(Function<RuleData, Mono<Boolean>> listener) {
        return this
                .accept()
                .flatMap((data) -> listener
                        .apply(data)
                        .onErrorResume(err -> Mono.empty()))
                .subscribe();
    }
}
