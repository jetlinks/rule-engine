package org.jetlinks.rule.engine.defaults;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.topic.Topic;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.codec.Codecs;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@Slf4j
@SuppressWarnings("all")
public class LocalEventBus implements EventBus {

    private final Topic<FluxSink<SubscribePayload>> subs = Topic.createRoot();

    @Override
    public <T> Flux<T> subscribe(String topic, Decoder<T> decoder) {
        return subscribe(topic)
                .map(v -> {
                    Payload payload = v.getPayload();
                    if (payload instanceof NativePayload) {
                        return (T) ((NativePayload) payload).getNativeObject();
                    }
                    return decoder.decode(v.getPayload());
                });
    }

    @Override
    public <T> Mono<Integer> publish(String topic, Publisher<T> event) {
        return publish(topic, (Function<T, ByteBuf>) v -> Codecs.lookup((Class) v.getClass()).encode(v).getBody(), event);
    }

    public <T> Mono<Integer> publish(String topic, Function<T, ByteBuf> encoder, Publisher<? extends T> eventStream) {
        return subs
                .findTopic(topic)
                .map(Topic::getSubscribers)
                .flatMap(subscriber -> {
                    log.debug("publish topic: {}", topic);
                    return Flux
                            .from(eventStream)
                            .doOnNext(data -> {
                                for (FluxSink<SubscribePayload> fluxSink : subscriber) {
                                    try {
                                        fluxSink.next(SubscribePayload.of(topic, NativePayload.of(data, () -> encoder.apply(data))));
                                    } catch (Exception e) {
                                        log.error(e.getMessage(), e);
                                    }
                                }
                            })
                            .then(Mono.just(subscriber.size()));
                })
                .reduce(Math::addExact)
                ;
    }

    @Override
    public <T> Mono<Integer> publish(String topic, Encoder<T> encoder, Publisher<? extends T> eventStream) {
        return publish(topic, (Function<T, ByteBuf>) v -> encoder.encode(v).getBody(), eventStream);
    }

    @Override
    public Flux<SubscribePayload> subscribe(String topic) {
        return Flux.<SubscribePayload>create((sink) -> {
            log.debug("subscription topic: {}", topic);
            Topic<FluxSink<SubscribePayload>> sub = subs.append(topic);
            sub.subscribe(sink);
            sink.onDispose(() -> {
                log.debug("unsubscription topic: {}", topic);
                sub.unsubscribe(sink);
            });
        });
    }
}
