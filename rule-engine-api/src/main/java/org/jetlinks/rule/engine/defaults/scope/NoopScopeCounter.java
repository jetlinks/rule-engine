package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.ScopeCounter;
import reactor.core.publisher.Mono;


/**
 * 空操作的作用域计数器实现
 * <p>
 * 该实现不执行任何实际的计数操作,所有方法都返回空的Mono,
 * 通常用于测试或者不需要计数功能的场景
 *
 * @author zhouhao
 * @since 1.1.1
 */
public class NoopScopeCounter implements ScopeCounter {

    public static final NoopScopeCounter INSTANCE = new NoopScopeCounter();

    private NoopScopeCounter() {
    }

    /**
     * 递增计数器值
     *
     * @param n 递增的数值
     * @return 空的Mono,不返回任何值
     */
    @Override
    public Mono<Double> inc(double n) {
        return Mono.empty();
    }

    /**
     * 递减计数器值
     *
     * @param n 递减的数值
     * @return 空的Mono,不返回任何值
     */
    @Override
    public Mono<Double> dec(double n) {
        return Mono.empty();
    }

    /**
     * 获取计数器当前值
     *
     * @return 空的Mono,不返回任何值
     */
    @Override
    public Mono<Double> get() {
        return Mono.empty();
    }

    /**
     * 设置计数器的值
     *
     * @param value 新的值
     * @return 空的Mono,不返回任何值
     */
    @Override
    public Mono<Double> set(double value) {
        return Mono.empty();
    }

    /**
     * 设置计数器的值并返回设置后的值
     *
     * @param value 要设置的值
     * @return 空的Mono,不返回任何值
     */
    @Override
    public Mono<Double> setAndGet(double value) {
        return Mono.empty();
    }

    /**
     * 获取计数器当前值并设置新值
     *
     * @param value 要设置的新值
     * @return 空的Mono,不返回任何值
     */
    @Override
    public Mono<Double> getAndSet(double value) {
        return Mono.empty();
    }

    /**
     * 移除计数器
     *
     * @return 空的Mono,不返回任何值
     */
    @Override
    public Mono<Double> remove() {
        return Mono.empty();
    }
}
