package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.FlowScope;
import org.jetlinks.rule.engine.api.scope.GlobalScope;

public class NoopGlobalScope extends NoopPersistenceScope implements GlobalScope {

    public static final NoopGlobalScope INSTANCE = new NoopGlobalScope();

    private NoopGlobalScope(){}

    @Override
    public FlowScope flow(String id) {
        return NoopFlowScope.INSTANCE;
    }
}
