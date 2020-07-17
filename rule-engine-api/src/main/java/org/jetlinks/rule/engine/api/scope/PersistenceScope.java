package org.jetlinks.rule.engine.api.scope;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 支持持久化的作用域,可在作用域中设置,获取数据
 *
 * @author zhouhao
 * @since 1.1.1
 */
public interface PersistenceScope extends Scope {

    /**
     * 设置数据
     *
     * @param key   key
     * @param value value
     * @return void
     */
    Mono<Void> put(String key, Object value);

    /**
     * 设置多个数据
     *
     * @param keyValue keyValue
     * @return void
     */
    Mono<Void> putAll(Map<String, Object> keyValue);

    /**
     * 获取指定key的数据,如果没有指定key则返回全部数据
     *
     * @param key key array
     * @return value
     */
    Mono<Map<String, Object>> all(String... key);

    /**
     * 获取值
     *
     * @param key key
     * @return void
     */
    Mono<Object> get(String key);

    /**
     * 清空作用域数据,不会清空Counter
     *
     * @return void
     */
    Mono<Void> clear();

    /**
     * 获取一个计数器
     *
     * @param key key
     * @return 计数器
     */
    ScropeCounter counter(String key);

    default ScropeCounter counter() {
        return counter("_default");
    }

}
