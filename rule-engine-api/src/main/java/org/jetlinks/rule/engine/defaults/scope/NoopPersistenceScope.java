package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.PersistenceScope;
import org.jetlinks.rule.engine.api.scope.ScopeCounter;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 空实现的持久化作用域
 * <p>
 * 所有方法都返回空的Mono,不执行任何实际操作.
 * 通常用于测试或者不需要持久化功能的场景.
 *
 * @author zhouhao
 * @since 1.1.1
 */
public class NoopPersistenceScope implements PersistenceScope {
    /**
     * 设置数据(空实现)
     *
     * @param key   键
     * @param value 值
     * @return 空的Mono
     */
    @Override
    public Mono<Void> put(String key, Object value) {
        return Mono.empty();
    }

    /**
     * 批量设置数据(空实现)
     *
     * @param keyValue 键值对集合
     * @return 空的Mono
     */
    @Override
    public Mono<Void> putAll(Map<String, Object> keyValue) {
        return Mono.empty();
    }

    /**
     * 获取指定key的数据,如果没有指定key则返回全部数据(空实现)
     *
     * @param key 键数组
     * @return 空的Mono
     */
    @Override
    public Mono<Map<String, Object>> all(String... key) {
        return Mono.empty();
    }

    /**
     * 获取指定键的值(空实现)
     *
     * @param key 键
     * @return 空的Mono
     */
    @Override
    public Mono<Object> get(String key) {
        return Mono.empty();
    }

    /**
     * 获取值并删除(空实现)
     *
     * @param key 键
     * @return 空的Mono
     */
    @Override
    public Mono<Object> getAndRemove(String key) {
        return Mono.empty();
    }

    /**
     * 删除指定键的值(空实现)
     *
     * @param key 键
     * @return 空的Mono
     */
    @Override
    public Mono<Object> remove(String key) {
        return Mono.empty();
    }

    /**
     * 清空作用域数据(空实现)
     *
     * @return 空的Mono
     */
    @Override
    public Mono<Void> clear() {
        return Mono.empty();
    }

    /**
     * 获取一个计数器(空实现)
     *
     * @param key 计数器的键
     * @return 空实现的计数器实例
     */
    @Override
    public ScopeCounter counter(String key) {
        return NoopScopeCounter.INSTANCE;
    }
}
