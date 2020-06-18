package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.api.rpc.RpcServiceFactory;
import org.jetlinks.rule.engine.defaults.rpc.DefaultRpcServiceFactory;
import org.jetlinks.rule.engine.defaults.rpc.EventBusRcpService;
import org.jetlinks.rule.engine.cluster.scheduler.ClusterLocalScheduler;
import org.jetlinks.rule.engine.defaults.LocalEventBus;
import org.junit.Test;
import reactor.test.StepVerifier;

public class ClusterSchedulerRegistryTest {


    EventBus eventBus = new LocalEventBus();
    RpcService rpcService = new EventBusRcpService(new LocalEventBus());


    @Test
    public void test() {
        RpcServiceFactory factory=new DefaultRpcServiceFactory(rpcService);

        {
            ClusterSchedulerRegistry registry = new ClusterSchedulerRegistry(eventBus, factory);
            registry.setup();

            ClusterLocalScheduler scheduler = new ClusterLocalScheduler("test", factory);
            registry.register(scheduler);
        }

        ClusterSchedulerRegistry registry = new ClusterSchedulerRegistry(eventBus, factory);
        registry.setup();
        ClusterLocalScheduler scheduler = new ClusterLocalScheduler("test2", factory);

        registry.register(scheduler);

        registry.getSchedulers()
                .as(StepVerifier::create)
                .expectNextCount(2)
                .verifyComplete();

    }

}