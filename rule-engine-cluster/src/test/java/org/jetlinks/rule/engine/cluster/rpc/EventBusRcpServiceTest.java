package org.jetlinks.rule.engine.cluster.rpc;

import org.jetlinks.rule.engine.api.rpc.RpcDefinition;
import org.jetlinks.rule.engine.defaults.LocalEventBus;
import org.junit.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

public class EventBusRcpServiceTest {


    @Test
    public void test() {
        EventBusRcpService rcpService = new EventBusRcpService(new LocalEventBus());

        RpcDefinition<String, String> definition = RpcDefinition.of("/lower_case", String.class, String.class);

        Disposable disposable = rcpService.listen(definition, (addr, request) -> Mono.just(request.toLowerCase()));

        rcpService.invoke(definition, "HELLO")
                .as(StepVerifier::create)
                .expectNext("hello")
                .verifyComplete();

        disposable.dispose();

    }

    @Test
    public void testReturnFlux() {
        EventBusRcpService rcpService = new EventBusRcpService(new LocalEventBus());

        RpcDefinition<Integer, Integer> definition = RpcDefinition.of("/generic", Integer.class, Integer.class);

        Disposable disposable = rcpService.listen(definition, (addr, request) -> Flux.range(0,request).delayElements(Duration.ofMillis(100)));

        rcpService.invoke(definition, 10)
                .as(StepVerifier::create)
                .expectNextCount(10)
                .verifyComplete();

        rcpService.invoke(definition, Flux.just(4,4,4))
                .as(StepVerifier::create)
                .expectNextCount(12)
                .verifyComplete();

        disposable.dispose();

    }

}