package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.PersistenceScope;
import org.jetlinks.rule.engine.api.scope.ScropeCounter;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryPersistenceScope implements PersistenceScope {

    private final ConcurrentMap<String, Object> store = new ConcurrentHashMap<>();

    private final Map<String, ScropeCounter> counter = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> put(String key, Object value) {
        return Mono.fromRunnable(() -> store.put(key, value));
    }

    @Override
    public Mono<Void> putAll(Map<String, Object> keyValue) {
        return Mono.fromRunnable(() -> store.putAll(keyValue));
    }

    @Override
    public Mono<Map<String, Object>> all(String... key) {
        return Mono.fromSupplier(() -> {
            if (key.length == 0) {
                return store;
            }
            Map<String, Object> vals = new HashMap<>();
            for (String s : key) {
                vals.put(s, store.get(s));
            }
            return vals;
        });
    }

    @Override
    public Mono<Object> get(String key) {
        return Mono.fromSupplier(() -> store.get(key));
    }

    @Override
    public Mono<Void> clear() {
        return Mono.fromRunnable(store::clear);
    }

    @Override
    public ScropeCounter counter(String key) {
        return counter.computeIfAbsent(key, (k) -> new InMemoryScopeCounter());
    }
}
