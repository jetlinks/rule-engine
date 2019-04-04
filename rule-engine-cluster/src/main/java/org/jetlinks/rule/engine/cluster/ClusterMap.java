package org.jetlinks.rule.engine.cluster;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface ClusterMap<K, V> {

    Optional<V> get(K key);

    CompletionStage<V> getAsync(K key);

    void put(K key, V value);

    CompletionStage<V> putAsync(K key, V value);

    V remove(K key);

    void putAll(Map<K, V> source);

    CompletionStage<V> removeAsync(K key);

    Map<K, V> toMap();

    void clear();

}
