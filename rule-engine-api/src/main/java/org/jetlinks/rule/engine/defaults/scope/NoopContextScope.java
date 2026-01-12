package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.ContextScope;

public class NoopContextScope extends NoopPersistenceScope implements ContextScope {
    public static final NoopContextScope INSTANCE = new NoopContextScope();

    private NoopContextScope(){}

}
