package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.NodeScope;

public class NoopNodeScope extends NoopPersistenceScope implements NodeScope {
    public static final NoopNodeScope INSTANCE = new NoopNodeScope();

    private NoopNodeScope(){}

}
