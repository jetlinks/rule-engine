package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.cluster.rpc.EventBusRcpService;
import org.jetlinks.rule.engine.cluster.scheduler.ClusterLocalScheduler;
import org.jetlinks.rule.engine.cluster.task.MockTask;
import org.jetlinks.rule.engine.defaults.LocalEventBus;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.Assert.*;

public class ClusterSchedulerRegistryTest {


    EventBus eventBus = new LocalEventBus();
    RpcService rpcService = new EventBusRcpService(new LocalEventBus());


    @Test
    public void test() {
        {
            ClusterSchedulerRegistry registry = new ClusterSchedulerRegistry(eventBus, rpcService);
            registry.setup();

            ClusterLocalScheduler scheduler = new ClusterLocalScheduler("test", rpcService);
            scheduler.setup();
            registry.register(scheduler);
        }

        ClusterSchedulerRegistry registry = new ClusterSchedulerRegistry(eventBus, rpcService);
        registry.setup();
        ClusterLocalScheduler scheduler = new ClusterLocalScheduler("test2", rpcService);
        scheduler.setup();

        registry.register(scheduler);

        registry.getSchedulers()
                .as(StepVerifier::create)
                .expectNextCount(2)
                .verifyComplete();

    }

}