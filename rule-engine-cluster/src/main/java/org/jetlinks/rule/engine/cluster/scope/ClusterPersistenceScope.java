package org.jetlinks.rule.engine.cluster.scope;

import lombok.AllArgsConstructor;
import org.jetlinks.core.cluster.ClusterCache;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.scope.PersistenceScope;
import org.jetlinks.rule.engine.api.scope.ScopeCounter;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Map;

@AllArgsConstructor
class ClusterPersistenceScope implements PersistenceScope {

    protected final String id;

    protected final ClusterManager clusterManager;

    protected String getKey() {
        return "rule-engine:" + id;
    }

    @Override
    public Mono<Void> putAll(Map<String, Object> keyValue) {
        return getCache()
                .putAll(keyValue)
                .then();
    }

    protected ClusterCache<String, Object> getCache() {
        return clusterManager.getCache(getKey());
    }

    @Override
    public Mono<Map<String, Object>> all(String... key) {

        if (key.length == 0) {
            return getCache()
                    .entries()
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue);
        }

        return getCache()
                .get(Arrays.asList(key))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    @Override
    public Mono<Void> put(String key, Object value) {
        if (value == null) {
            return getCache()
                    .remove(key)
                    .then();
        }
        return getCache()
                .put(key, value)
                .then();
    }


    @Override
    public Mono<Object> remove(String key) {
        return getCache()
                .get(key)
                .flatMap(v -> getCache()
                        .remove(key)
                        .thenReturn(v)
                );
    }

    @Override
    public Mono<Object> get(String key) {
        return getCache()
                .get(key);
    }

    @Override
    public Mono<Object> getAndRemove(String key) {
        return getCache().getAndRemove(key);
    }

    @Override
    public Mono<Void> clear() {
        return getCache()
                .clear();
    }

    @Override
    public ScopeCounter counter(String key) {
        return new ClusterScopeCounter(clusterManager.getCounter(getKey() + ":counter"));
    }
}
