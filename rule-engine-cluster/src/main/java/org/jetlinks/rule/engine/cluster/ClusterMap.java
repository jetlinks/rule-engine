package org.jetlinks.rule.engine.cluster;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface ClusterMap<K, V> {

    Optional<V> get(K key);

    CompletionStage<V> getAsync(K key);

    void put(K key, V value);

    CompletionStage<V> putAsync(K key, V value);

    void remove(K key);

    CompletionStage<V> removeAsync(K key);

    Map<K, V> toMap();

}
