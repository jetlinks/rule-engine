package org.jetlinks.rule.engine.api.scope;

import reactor.core.publisher.Mono;

/**
 * 计数器
 *
 * @author zhouhao
 */
public interface ScopeCounter {

    /**
     * 递增 1
     *
     * @return 自增后的值
     */
    default Mono<Double> inc() {
        return inc(1);
    }

    /**
     * 递减 1
     *
     * @return 递减后等值
     */
    default Mono<Double> dec() {
        return dec(1);
    }

    /**
     * 递增 n
     *
     * @return 自增后的值
     */
    Mono<Double> inc(double n);

    /**
     * 递减 n
     *
     * @return 递减后等值
     */
    Mono<Double> dec(double n);

    /**
     * 获取当前值
     *
     * @return 当前值
     */
    Mono<Double> get();

    /**
     * 设置值
     *
     * @param value 新的值
     * @return 旧的值
     */
    Mono<Double> set(double value);

    /**
     * 设置值并返回最新的值
     *
     * @param value 值
     * @return 最新的值
     */
    Mono<Double> setAndGet(double value);

    /**
     * 获取值然后更新
     *
     * @param value 值
     * @return 更新前的值
     */
    Mono<Double> getAndSet(double value);

}
