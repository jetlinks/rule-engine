package org.jetlinks.rule.engine.cluster.redis;

import lombok.SneakyThrows;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.rule.engine.cluster.AbstractClusterRuleEngineTest;
import org.jetlinks.supports.cluster.event.RedisClusterEventBroker;
import org.jetlinks.supports.cluster.redis.RedisClusterManager;
import org.jetlinks.supports.event.BrokerEventBus;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.util.concurrent.atomic.AtomicInteger;

public class RedisClusterRuleEngineTest extends AbstractClusterRuleEngineTest {


    AtomicInteger inc = new AtomicInteger(1);


    @Override
    @SneakyThrows
    public EventBus getEventBus() {
        BrokerEventBus eventBus = new BrokerEventBus();
        eventBus.setLog(LoggerFactory.getLogger("org.jetlinks.eventbus."+inc));
        ReactiveRedisConnectionFactory factory = RedisHelper.connectionFactory();
        RedisClusterManager clusterManager = new RedisClusterManager(
                "test2", "test-" + inc.getAndIncrement(), new ReactiveRedisTemplate<>(factory, RedisSerializationContext.java()));
        clusterManager.startup();
        Thread.sleep(1000);
        RedisClusterEventBroker broker = new RedisClusterEventBroker(clusterManager, factory);

        eventBus.addBroker(broker);
        broker.startup();
        Thread.sleep(1000);
        return eventBus;
    }

}