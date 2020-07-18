package org.jetlinks.rule.engine.cluster.scope;

import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.scope.FlowScope;
import org.jetlinks.rule.engine.api.scope.GlobalScope;

public class ClusterGlobalScope extends ClusterPersistenceScope implements GlobalScope {

    public ClusterGlobalScope(ClusterManager clusterManager) {
        super("scope:global", clusterManager);
    }

    @Override
    public FlowScope flow(String id) {
        return new ClusterFlowScope("scope:flow:" + id, clusterManager);
    }

}
