package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.ContextScope;
import org.jetlinks.rule.engine.api.scope.FlowScope;
import org.jetlinks.rule.engine.api.scope.NodeScope;

public class NoopFlowScope extends NoopPersistenceScope implements FlowScope {

    public static final NoopFlowScope INSTANCE = new NoopFlowScope();

    private NoopFlowScope(){}

    @Override
    public NodeScope node(String id) {
        return NoopNodeScope.INSTANCE;
    }

    @Override
    public ContextScope context(String id) {
        return NoopContextScope.INSTANCE;
    }
}
