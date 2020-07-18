package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.FlowScope;
import org.jetlinks.rule.engine.api.scope.GlobalScope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGlobalScope extends InMemoryPersistenceScope implements GlobalScope {

    private final Map<String, FlowScope> flowScopeMap = new ConcurrentHashMap<>();

    @Override
    public FlowScope flow(String id) {
        return flowScopeMap.computeIfAbsent(id, ignore -> new InMemoryFlowScope());
    }
}
