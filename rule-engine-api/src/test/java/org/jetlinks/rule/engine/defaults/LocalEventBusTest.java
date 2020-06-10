package org.jetlinks.rule.engine.defaults;

import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

public class LocalEventBusTest {


    @Test
    public void test() {

        LocalEventBus eventBus = new LocalEventBus();


        AtomicReference<Integer> ref = new AtomicReference<>();

        eventBus.subscribe("/test/*", Integer.class)
                .take(1)
                .subscribe(ref::set);

        eventBus.publish("/test/a", Flux.just(1))
                .as(StepVerifier::create)
                .expectNext(1)
                .verifyComplete()
        ;

        Assert.assertEquals(ref.get(),new Integer(1));

    }

}