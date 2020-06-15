package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CompositeEventBus implements EventBus {

    private final List<EventBus> subBus = new CopyOnWriteArrayList<>();

    private final List<EventBus> pubBus = new CopyOnWriteArrayList<>();

    public CompositeEventBus addForSubscribe(EventBus... bus) {
        subBus.addAll(Arrays.asList(bus));
        return this;
    }

    public CompositeEventBus addForPublish(EventBus... bus) {
        pubBus.addAll(Arrays.asList(bus));
        return this;
    }

    @Override
    public <T> Flux<T> subscribe(String topic, Decoder<T> type) {
        return Flux.fromIterable(subBus)
                .map(eventBus -> eventBus.subscribe(topic, type))
                .as(Flux::merge);
    }

    @Override
    public <T> Mono<Integer> publish(String topic, Encoder<T> encoder, Publisher<? extends T> eventStream) {
        Flux<? extends T> source = Flux.from(eventStream).cache();

        return Flux.fromIterable(pubBus)
                .map(eventBus -> eventBus.publish(topic, encoder, source))
                .as(Flux::merge)
                .reduce(Math::addExact)
                .defaultIfEmpty(0);
    }

    @Override
    public Flux<SubscribePayload> subscribe(String topic) {
        return Flux.fromIterable(subBus)
                .map(eventBus -> eventBus.subscribe(topic))
                .as(Flux::merge);
    }

    @Override
    public <T> Mono<Integer> publish(String topic, Publisher<T> event) {
        Flux<? extends T> source = Flux.from(event).cache();

        return Flux.fromIterable(pubBus)
                .map(eventBus -> eventBus.publish(topic, source))
                .as(Flux::merge)
                .reduce(Math::addExact)
                .defaultIfEmpty(0);
    }
}
