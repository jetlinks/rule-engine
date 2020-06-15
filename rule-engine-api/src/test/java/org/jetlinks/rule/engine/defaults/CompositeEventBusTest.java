package org.jetlinks.rule.engine.defaults;

import org.junit.Assert;
import org.junit.Test;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicLong;

public class CompositeEventBusTest {

    @Test
    public void test() {
        CompositeEventBus eventBus = new CompositeEventBus();

        LocalEventBus local1 = new LocalEventBus();
        LocalEventBus local2 = new LocalEventBus();

        //推送到1，2
        eventBus.addForPublish(local1, local2);

        //只从1订阅
        eventBus.addForSubscribe(local1);

        {
            AtomicLong count = new AtomicLong();

            local1.subscribe("/test", Integer.class)
                    .doOnNext(count::set)
                    .subscribe();

            eventBus.publish("/test", 1)
                    .as(StepVerifier::create)
                    .expectNextCount(1)
                    .verifyComplete();
            Assert.assertEquals(count.get(), 1);

        }


        {
            AtomicLong count = new AtomicLong();

            eventBus.subscribe("/test", Integer.class)
                    .doOnNext(count::set)
                    .subscribe();

            local1.publish("/test", 1)
                    .as(StepVerifier::create)
                    .expectNextCount(1)
                    .verifyComplete();

            Assert.assertEquals(count.get(), 1);
        }


    }

}