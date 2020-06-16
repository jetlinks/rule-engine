package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.defaults.rpc.EventBusRcpService;
import org.jetlinks.rule.engine.defaults.LocalEventBus;

public class ClusterRuleEngineTest extends AbstractClusterRuleEngineTest{

    EventBus eventBus = new LocalEventBus();
    RpcService rpcService = new EventBusRcpService(eventBus);

    @Override
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public RpcService getRpcService() {
        return rpcService;
    }
}