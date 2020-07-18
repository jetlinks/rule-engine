package org.jetlinks.rule.engine.cluster.scope;

import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.scope.ContextScope;
import org.jetlinks.rule.engine.api.scope.FlowScope;
import org.jetlinks.rule.engine.api.scope.NodeScope;

class ClusterFlowScope extends ClusterPersistenceScope implements FlowScope {
    public ClusterFlowScope(String id, ClusterManager clusterManager) {
        super(id, clusterManager);
    }
    @Override
    public NodeScope node(String id) {
        return new ClusterNodeScope(this.id + ":node:" + id, clusterManager);
    }

    @Override
    public ContextScope context(String id) {
        return new ClusterContextScope(this.id + ":context:" + id, clusterManager);
    }
}
