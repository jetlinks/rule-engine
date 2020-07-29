package org.jetlinks.rule.engine.cluster.scheduler;

import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.rpc.RpcService;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.TaskExecutor;
import org.jetlinks.rule.engine.api.task.TaskExecutorProvider;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.defaults.AbstractTaskExecutor;
import org.jetlinks.rule.engine.defaults.LocalWorker;
import org.jetlinks.supports.event.BrokerEventBus;
import org.jetlinks.supports.rpc.DefaultRpcServiceFactory;
import org.jetlinks.supports.rpc.EventBusRcpService;
import org.junit.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.Function;

public class ClusterSchedulerTest {

    EventBus eventBus = new BrokerEventBus();
    RpcService rpcService = new EventBusRcpService(eventBus);

    @Test
    public void test() {

        DefaultRpcServiceFactory factory = new DefaultRpcServiceFactory(rpcService);

        ClusterLocalScheduler scheduler = new ClusterLocalScheduler("test", factory);

        LocalWorker worker = new LocalWorker("worker1", "测试", eventBus, (r, v) -> true);

        worker.addExecutor(new TaskExecutorProvider() {
            @Override
            public String getExecutor() {
                return "test";
            }

            @Override
            public Mono<TaskExecutor> createTask(ExecutionContext context) {
                return Mono.just(new AbstractTaskExecutor(context) {
                    @Override
                    public String getName() {
                        return "测试";
                    }

                    @Override
                    protected Disposable doStart() {
                        return null;
                    }
                });
            }
        });
        scheduler.addWorker(worker);

        RemoteScheduler remoteScheduler = new RemoteScheduler("test", factory);
        remoteScheduler.init();
        remoteScheduler
                .getWorker(worker.getId())
                .map(Worker::getName)
                .as(StepVerifier::create)
                .expectNext(worker.getName())
                .verifyComplete();

        remoteScheduler.getWorker(worker.getId())
                .flatMap(Worker::getSupportExecutors)
                .flatMapIterable(Function.identity())
                .as(StepVerifier::create)
                .expectNext("test")
                .verifyComplete();

    }
}