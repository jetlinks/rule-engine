package org.jetlinks.rule.engine.cluster.redis;

import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.cluster.AbstractClusterRuleEngineTest;
import org.jetlinks.rule.engine.cluster.rpc.EventBusRcpService;

public class RedisClusterRuleEngineTest extends AbstractClusterRuleEngineTest {

    EventBus eventBus=new RedisEventBus(RedisHelper.connectionFactory());
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