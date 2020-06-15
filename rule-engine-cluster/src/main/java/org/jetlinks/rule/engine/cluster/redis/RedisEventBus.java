package org.jetlinks.rule.engine.cluster.redis;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.topic.Topic;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.codec.Codecs;
import org.reactivestreams.Publisher;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class RedisEventBus implements EventBus {

    private final ReactiveRedisOperations<String, ByteBuf> redisTemplate;

    private final static RedisSerializer<ByteBuf> serializer = new RedisSerializer<ByteBuf>() {
        @Override
        public byte[] serialize(ByteBuf byteBuf) throws SerializationException {
            return ByteBufUtil.getBytes(byteBuf);
        }

        @Override
        public ByteBuf deserialize(byte[] bytes) throws SerializationException {
            return Unpooled.wrappedBuffer(bytes);
        }
    };

    private final Topic<FluxSink<SubscribePayload>> topic = Topic.createRoot();

    private final String prefix;

    public RedisEventBus(ReactiveRedisConnectionFactory connectionFactory) {
        this("/event-bus", connectionFactory);
    }

    public RedisEventBus(String prefix, ReactiveRedisConnectionFactory connectionFactory) {
        this.prefix = prefix;
        redisTemplate = new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext
                .<String, ByteBuf>newSerializationContext()
                .key(RedisSerializer.string())
                .value(serializer)
                .hashKey(RedisSerializer.string())
                .hashValue(serializer)
                .build());
        init();
    }

    public void init() {
        redisTemplate
                .listenToPattern(prefix + "*")
                .publishOn(Schedulers.parallel())
                .subscribe(msg -> {
                    String channel = msg.getChannel().substring(prefix.length());
                    topic.findTopic(channel)
                            .map(Topic::getSubscribers)
                            .distinct()
                            .subscribe(subs -> {
                                for (FluxSink<SubscribePayload> sub : subs) {
                                    try {
                                        sub.next(SubscribePayload.of(channel, Payload.of(msg.getMessage())));
                                    } catch (Throwable e) {
                                        log.error(e.getMessage(), e);
                                    }
                                }
                            });
                });
    }

    private String wrapRedisTopic(String topic) {
        return prefix + topic;
    }

    @Override
    public <T> Flux<T> subscribe(String topic, Decoder<T> type) {

        return subscribe(topic)
                .flatMap(payload -> Mono.justOrEmpty(type.decode(payload)));
    }

    @Override
    public <T> Mono<Integer> publish(String topic, Encoder<T> encoder, Publisher<? extends T> eventStream) {

        return Flux.from(eventStream)
                .flatMap(data -> redisTemplate.convertAndSend(wrapRedisTopic(topic), encoder.encode(data).getBody()))
                .reduce(Math::addExact)
                .map(Long::intValue)
                ;
    }

    @Override
    public Flux<SubscribePayload> subscribe(String topic) {

        return Flux.create(sink -> {
            log.debug("subscribe topic {}", topic);

            Topic<FluxSink<SubscribePayload>> sub = this.topic.append(topic);
            sub.subscribe(sink);

            sink.onDispose(() -> sub.unsubscribe(sink));
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    @Override
    public <T> Mono<Integer> publish(String topic, Publisher<T> event) {

        return Flux.from(event)
                .flatMap(data -> redisTemplate.convertAndSend(wrapRedisTopic(topic), Codecs.lookup((Class<T>) data.getClass()).encode(data).getBody()))
                .reduce(Math::addExact)
                .map(Long::intValue);
    }
}
