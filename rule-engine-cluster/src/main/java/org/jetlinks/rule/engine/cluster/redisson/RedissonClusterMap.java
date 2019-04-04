package org.jetlinks.rule.engine.cluster.redisson;

import org.jetlinks.rule.engine.cluster.ClusterMap;
import org.redisson.api.RMap;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class RedissonClusterMap<K, V> implements ClusterMap<K, V> {

    private RMap<K, V> map;

    public RedissonClusterMap(RMap<K, V> map) {
        this.map = map;
    }

    @Override
    public Optional<V> get(K key) {
        return Optional.of(map.get(key));
    }

    @Override
    public CompletionStage<V> getAsync(K key) {
        return map.getAsync(key);
    }

    @Override
    public void put(K key, V value) {
        map.fastPutAsync(key, value);
    }

    @Override
    public CompletionStage<V> putAsync(K key, V value) {
        return map.putAsync(key, value);
    }

    @Override
    public void remove(K key) {
        map.fastRemove(key);
    }

    @Override
    public void putAll(Map<K, V> source) {
        map.putAll(source);
    }

    @Override
    public CompletionStage<V> removeAsync(K key) {
        return map.removeAsync(key);
    }

    @Override
    public Map<K, V> toMap() {
        return map;
    }

    @Override
    public void clear() {
        map.clear();
    }
}
