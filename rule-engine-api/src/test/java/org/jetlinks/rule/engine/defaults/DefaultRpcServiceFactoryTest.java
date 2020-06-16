package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.defaults.rpc.EventBusRcpService;
import org.junit.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

public class DefaultRpcServiceFactoryTest {


    @Test
    public void test() {
        DefaultRpcServiceFactory factory = new DefaultRpcServiceFactory(new EventBusRcpService(new LocalEventBus()));


        TestService service = factory.createProducer("/test", TestService.class);

        TestServiceConsumer consumer = new TestServiceConsumer();

        Disposable disposable = factory.createConsumer("/test", TestService.class, consumer);

        service.sayHello()
                .as(StepVerifier::create)
                .expectNext("hello")
                .verifyComplete();

        service.sayYeah()
                .as(StepVerifier::create)
                .expectComplete()
                .verify();

        service.genericNumber(4)
                .as(StepVerifier::create)
                .expectNext(0, 1, 2, 3)
                .verifyComplete();

        service.createList("1","2")
                .as(StepVerifier::create)
                .expectNext(Arrays.asList("1","2"))
                .verifyComplete();


        disposable.dispose();
    }


    public interface TestService {
        Mono<String> sayHello();

        Mono<Void> sayYeah();

        Mono<List<String>> createList(String... args);

        Flux<Integer> genericNumber(int numbers);
    }

    public class TestServiceConsumer implements TestService {
        @Override
        public Flux<Integer> genericNumber(int numbers) {

            return Flux.range(0, numbers).delayElements(Duration.ofMillis(100));
        }

        @Override
        public Mono<String> sayHello() {

            return Mono.justOrEmpty("hello");
        }

        @Override
        public Mono<Void> sayYeah() {
            System.out.println("yeah");
            return Mono.empty();
        }

        @Override
        public Mono<List<String>> createList(String... args) {
            return Flux.fromArray(args)
                    .collectList();
        }
    }
}