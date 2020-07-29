package org.jetlinks.rule.engine.cluster;

import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.rpc.RpcService;
import org.jetlinks.supports.event.BrokerEventBus;
import org.jetlinks.supports.rpc.EventBusRcpService;
import reactor.core.scheduler.Schedulers;

public class ClusterRuleEngineTest extends AbstractClusterRuleEngineTest{

    BrokerEventBus eventBus = new BrokerEventBus();
    RpcService rpcService = new EventBusRcpService(eventBus);

    @Override
    public EventBus getEventBus() {
        eventBus.setPublishScheduler(Schedulers.immediate());
        return eventBus;
    }

    @Override
    public RpcService getRpcService() {
        return rpcService;
    }
}