package org.jetlinks.rule.engine.cluster.worker;

import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.TaskExecutor;
import org.jetlinks.rule.engine.api.task.TaskExecutorProvider;
import org.jetlinks.rule.engine.cluster.rpc.EventBusRcpService;
import org.jetlinks.rule.engine.defaults.FunctionTaskExecutor;
import org.jetlinks.rule.engine.defaults.LocalEventBus;
import org.jetlinks.rule.engine.defaults.LocalWorker;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicInteger;

public class ClusterWorkerTest {

    EventBus eventBus = new LocalEventBus();
    RpcService rpcService = new EventBusRcpService(new LocalEventBus());

    @Test
    public void test() {

        LocalWorker localWorker = new LocalWorker("test", "测试", eventBus, (condition, context) -> true);

        AtomicInteger counter = new AtomicInteger();

        localWorker.addExecutor(new TaskExecutorProvider() {
            @Override
            public String getExecutor() {
                return "test";
            }

            @Override
            public Mono<TaskExecutor> createTask(ExecutionContext context) {
                return Mono.just(new FunctionTaskExecutor("test", context) {
                    @Override
                    protected Publisher<RuleData> apply(RuleData input) {
                        counter.incrementAndGet();
                        return Mono.just(input.newData("world"));
                    }
                });
            }
        });

        ClusterLocalWorker clusterLocalWorker = new ClusterLocalWorker(localWorker, rpcService);
        clusterLocalWorker.setup();

        RemoteWorker worker = new RemoteWorker("test", "测试", rpcService);
        ScheduleJob job = new ScheduleJob();
        job.setInstanceId("test-rule");
        job.setNodeId("test-node");
        job.setExecutor("test");
        worker.createTask("test", job)
                .flatMap(task->task.start().thenReturn(task))
                .flatMap(task -> task.execute(Flux.just(RuleData.create("hello"),RuleData.create("hello2"))))
                .as(StepVerifier::create)
                .expectComplete()
                .verify();


    }

}