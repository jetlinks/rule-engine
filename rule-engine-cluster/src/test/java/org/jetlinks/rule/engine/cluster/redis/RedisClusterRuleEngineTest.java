package org.jetlinks.rule.engine.cluster.redis;

import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.rpc.RpcService;
import org.jetlinks.rule.engine.cluster.AbstractClusterRuleEngineTest;
import org.jetlinks.supports.cluster.event.RedisClusterEventBroker;
import org.jetlinks.supports.cluster.redis.RedisClusterManager;
import org.jetlinks.supports.event.BrokerEventBus;
import org.jetlinks.supports.rpc.EventBusRcpService;
import org.junit.Before;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

public class RedisClusterRuleEngineTest extends AbstractClusterRuleEngineTest {

    BrokerEventBus eventBus = new BrokerEventBus();
    RpcService rpcService = new EventBusRcpService(eventBus);

    @Before
    public void init() {
        ReactiveRedisConnectionFactory factory = RedisHelper.connectionFactory();

        eventBus.addBroker(new RedisClusterEventBroker(new RedisClusterManager("test", "test", new ReactiveRedisTemplate<>(factory, RedisSerializationContext.java())), factory));
    }

    @Override
    public EventBus getEventBus() {
        return eventBus;
    }

    @Override
    public RpcService getRpcService() {
        return rpcService;
    }
}