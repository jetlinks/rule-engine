package org.jetlinks.rule.engine.cluster.redis;

import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class RedisEventBusTest {


    @Test
    @SneakyThrows
    public void test(){

        RedisEventBus eventBus=new RedisEventBus(UUID.randomUUID().toString(),RedisHelper.connectionFactory());

        AtomicReference<Integer> ref = new AtomicReference<>();

        eventBus.subscribe("/test/*/a", Integer.class)
                .take(1)
                .subscribe(ref::set);
        eventBus.publish("/test/a/a", Flux.just(1))
                .as(StepVerifier::create)
                .expectNext(1)
                .verifyComplete()
        ;
        Thread.sleep(1000);
        Assert.assertEquals(ref.get(),new Integer(1));
    }

}