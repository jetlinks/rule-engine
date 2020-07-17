package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.ScropeCounter;
import org.jetlinks.rule.engine.api.scope.FlowScope;
import org.jetlinks.rule.engine.api.scope.GlobalScope;
import reactor.core.publisher.Mono;

import java.util.Map;

public class ClusterGlobalScope implements GlobalScope {

    @Override
    public FlowScope flow(String id) {
        return null;
    }

    @Override
    public Mono<Void> putAll(Map<String, Object> keyValue) {
        return null;
    }

    @Override
    public Mono<Map<String, Object>> all(String... key) {
        return null;
    }

    @Override
    public Mono<Void> put(String key, Object value) {
        return null;
    }

    @Override
    public Mono<Object> get(String key) {
        return null;
    }

    @Override
    public Mono<Void> clear() {
        return null;
    }

    @Override
    public ScropeCounter counter(String key) {
        return null;
    }
}
