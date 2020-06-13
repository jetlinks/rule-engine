package org.jetlinks.rule.engine.cluster.task;

import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.ScheduleJob;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.cluster.rpc.EventBusRcpService;
import org.jetlinks.rule.engine.defaults.LocalEventBus;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.BiConsumer;

public class ClusterTaskTest {

    EventBus eventBus = new LocalEventBus();
    RpcService rpcService = new EventBusRcpService(new LocalEventBus());

    @Test
    public void test() {
        MockTask mockTask = MockTask
                .builder()
                .id("test-task")
                .name("test")
                .workerId("test")
                .schedulerId("test")
                .build();

        ClusterLocalTask task = new ClusterLocalTask( mockTask, rpcService);

        task.setup();

        RemoteTask remoteTask = new RemoteTask("test-task", "test", "test", "test", rpcService, new ScheduleJob());

        BiConsumer<Mono<Task.State>, Task.State> verify = (stateMono, expect) -> {
            stateMono.as(StepVerifier::create)
                    .expectNextMatches(state -> state == mockTask.state && state == expect)
                    .verifyComplete();
        };

        verify.accept(remoteTask.start().then(remoteTask.getState()), Task.State.running);
        verify.accept(remoteTask.pause().then(remoteTask.getState()), Task.State.paused);
        verify.accept(remoteTask.shutdown().then(remoteTask.getState()), Task.State.shutdown);

        remoteTask.debug(true)
                .then(Mono.fromSupplier(mockTask::isDebug))
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();


    }
}