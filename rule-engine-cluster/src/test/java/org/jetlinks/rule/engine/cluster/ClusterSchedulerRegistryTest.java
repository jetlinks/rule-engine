package org.jetlinks.rule.engine.cluster;

import org.jetlinks.core.rpc.RpcService;
import org.jetlinks.core.rpc.RpcServiceFactory;
import org.jetlinks.rule.engine.cluster.scheduler.ClusterLocalScheduler;
import org.jetlinks.supports.event.BrokerEventBus;
import org.jetlinks.supports.rpc.DefaultRpcServiceFactory;
import org.jetlinks.supports.rpc.EventBusRcpService;
import org.junit.Test;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

public class ClusterSchedulerRegistryTest {


    BrokerEventBus eventBus = new BrokerEventBus();
    RpcService rpcService = new EventBusRcpService(eventBus);


    @Test
    public void test() {
        eventBus.setPublishScheduler(Schedulers.immediate());
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