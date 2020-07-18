package org.jetlinks.rule.engine.cluster.scope;

import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.scope.NodeScope;

class ClusterNodeScope extends ClusterPersistenceScope implements NodeScope {
    public ClusterNodeScope(String id, ClusterManager clusterManager) {
        super(id, clusterManager);
    }
}
