package org.jetlinks.rule.engine.cluster.scheduler;

import io.scalecube.cluster.ClusterConfig;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceInfo;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import io.scalecube.transport.netty.tcp.TcpTransportFactory;
import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.defaults.LocalScheduler;
import org.jetlinks.rule.engine.defaults.LocalWorker;
import org.jetlinks.supports.event.BrokerEventBus;
import org.jetlinks.supports.scalecube.DynamicServiceRegistry;
import org.jetlinks.supports.scalecube.ExtendedClusterImpl;
import org.jetlinks.supports.scalecube.ExtendedServiceDiscoveryImpl;
import org.junit.Before;
import org.junit.Test;
import reactor.test.StepVerifier;

public class ClusterSchedulerRegistryTest {
    ClusterSchedulerRegistry registry1, registry2, registry3;

    @Before
    @SneakyThrows
    public void init() {
        Microservices seed;
        {
            ExtendedClusterImpl cluster = new ExtendedClusterImpl(new ClusterConfig()
                                                                          .memberAlias("1")
                                                                          .transport(cfg -> cfg.transportFactory(new TcpTransportFactory()))
            );
            cluster.startAwait();

            LocalScheduler scheduler = new LocalScheduler("test1");
            scheduler.addWorker(new LocalWorker("test1", "test1", new BrokerEventBus(), (condition, context) -> true));

            ServiceInfo rcpService = ClusterSchedulerRegistry.createService(scheduler);

            seed = Microservices
                    .builder()
                    .discovery(serviceEndpoint -> new ExtendedServiceDiscoveryImpl(cluster, serviceEndpoint))
                    .transport(RSocketServiceTransport::new)
                    .serviceRegistry(new DynamicServiceRegistry())
                    .services(rcpService)
                    .startAwait();

            registry1 = new ClusterSchedulerRegistry(cluster, seed.call(), scheduler);
        }
        {
            ExtendedClusterImpl cluster = new ExtendedClusterImpl(new ClusterConfig()
                                                                          .memberAlias("2")
                                                                          .transport(cfg -> cfg.transportFactory(new TcpTransportFactory()))
                                                                          .membership(cfg -> cfg.seedMembers(seed
                                                                                                                     .discovery()
                                                                                                                     .address()))
            );
            cluster.startAwait();

            LocalScheduler scheduler = new LocalScheduler("test2");
            scheduler.addWorker(new LocalWorker("test2", "test2", new BrokerEventBus(), (condition, context) -> true));

            ServiceInfo rcpService = ClusterSchedulerRegistry.createService(scheduler);

            Microservices service2 = Microservices
                    .builder()
                    .discovery(serviceEndpoint -> new ExtendedServiceDiscoveryImpl(cluster, serviceEndpoint))
                    .transport(RSocketServiceTransport::new)
                    .serviceRegistry(new DynamicServiceRegistry())
                    .services(rcpService)
                    .startAwait();

            registry2 = new ClusterSchedulerRegistry(cluster, service2.call(), scheduler);

        }

        {
            ExtendedClusterImpl cluster = new ExtendedClusterImpl(new ClusterConfig()
                                                                          .memberAlias("3")
                                                                          .transport(cfg -> cfg.transportFactory(new TcpTransportFactory()))
                                                                          .membership(cfg -> cfg.seedMembers(seed
                                                                                                                     .discovery()
                                                                                                                     .address()))
            );
            cluster.startAwait();
            LocalScheduler scheduler = new LocalScheduler("test3");
            scheduler.addWorker(new LocalWorker("test3", "test3", new BrokerEventBus(), (condition, context) -> true));

            ServiceInfo rcpService = ClusterSchedulerRegistry.createService(scheduler);
            Microservices service3 = Microservices
                    .builder()
                    .discovery(serviceEndpoint -> new ExtendedServiceDiscoveryImpl(cluster, serviceEndpoint))
                    .transport(RSocketServiceTransport::new)
                    .serviceRegistry(new DynamicServiceRegistry())
                    .services(rcpService)
                    .startAwait();
            registry3 = new ClusterSchedulerRegistry(cluster, service3.call(), scheduler);

        }

        Thread.sleep(1000);
        registry1.getSchedulers()
                 .as(StepVerifier::create)
                 .expectNextCount(3)
                 .verifyComplete();

        registry2.getSchedulers()
                 .as(StepVerifier::create)
                 .expectNextCount(3)
                 .verifyComplete();

        registry3.getSchedulers()
                 .as(StepVerifier::create)
                 .expectNextCount(3)
                 .verifyComplete();
    }


    @Test
    public void test() {
        registry1.getSchedulers()
                 .flatMap(Scheduler::getWorkers)
                 .map(Worker::getId)
                 .sort()
                 .as(StepVerifier::create)
                 .expectNext("test1", "test2", "test3")
                 .verifyComplete();

        registry2.getSchedulers()
                 .flatMap(Scheduler::getWorkers)
                 .map(Worker::getId)
                 .sort()
                 .as(StepVerifier::create)
                 .expectNext("test1", "test2", "test3")
                 .verifyComplete();

        registry3.getSchedulers()
                 .flatMap(Scheduler::getWorkers)
                 .map(Worker::getId)
                 .sort()
                 .as(StepVerifier::create)
                 .expectNext("test1", "test2", "test3")
                 .verifyComplete();
    }
}