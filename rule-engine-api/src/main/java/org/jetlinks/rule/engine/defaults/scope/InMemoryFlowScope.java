package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.ContextScope;
import org.jetlinks.rule.engine.api.scope.FlowScope;
import org.jetlinks.rule.engine.api.scope.NodeScope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryFlowScope extends InMemoryPersistenceScope implements FlowScope {

    private final Map<String, NodeScope> nodeScopeMap = new ConcurrentHashMap<>();
    private final Map<String, ContextScope> contextScopeMap = new ConcurrentHashMap<>();


    @Override
    public NodeScope node(String id) {
        return nodeScopeMap.computeIfAbsent(id, k -> new InMemoryNodeScope());
    }

    @Override
    public ContextScope context(String id) {
        return contextScopeMap.computeIfAbsent(id, k -> new InMemoryContextScope());
    }
}
