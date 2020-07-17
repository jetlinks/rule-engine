package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.scope.ScropeCounter;
import org.jetlinks.rule.engine.api.scope.PersistenceScope;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;

public class ClusterPersistenceScope implements PersistenceScope {

    private ClusterManager clusterManager;

    private String id;


    private String getKey() {
        return "rule-engine:" + id;
    }

    @Override
    public Mono<Void> putAll(Map<String, Object> keyValue) {
        return clusterManager
                .getCache(getKey())
                .putAll(keyValue)
                .then();
    }

    @Override
    public Mono<Map<String, Object>> all(String... key) {

        if (key.length == 0) {
            return clusterManager.<String, Object>getCache(getKey())
                    .entries()
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue);
        }

        return clusterManager.<String, Object>getCache(getKey())
                .get(Arrays.asList(key))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    @Override
    public Mono<Void> put(String key, Object value) {

        return clusterManager
                .<String, Object>getCache(getKey())
                .put(key, value)
                .then();
    }

    @Override
    public Mono<Object> get(String key) {
        return clusterManager
                .<String, Object>getCache(getKey())
                .get(key);
    }

    @Override
    public Mono<Void> clear() {
        return clusterManager
                .<String, Object>getCache(getKey())
                .clear();
    }

    @Override
    public ScropeCounter counter(String key) {
        return null;
    }
}
