package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.codec.Codecs;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 基于订阅发布的事件总线,可用于事件传递,消息转发等.
 *
 * <pre>
 *      subscribe("/rule-engine/{rule-id}/{task-id}/input")
 * </pre>
 *
 * @author zhouhao
 * @see 1.1.0
 * @see org.jetlinks.core.topic.Topic
 */
public interface EventBus {

    /**
     * 订阅事件，Topic使用/分割。
     *
     * @param topic topic
     * @param <T>   事件类型泛型
     * @param type  事件类型
     * @return 事件流
     */
    <T> Flux<T> subscribe(String topic, Decoder<T> type);

    default <T> Flux<T> subscribe(String topic, Class<T> type) {
        return subscribe(topic, Codecs.lookup(type));
    }

    /**
     * 推送事件
     *
     * @param topic       Topic
     * @param eventStream 事件流
     * @return 接收的订阅者数量
     */
    <T> Mono<Integer> publish(String topic, Encoder<T> encoder, Publisher<? extends T> eventStream);

    default <T> Mono<Integer> publish(String topic, Encoder<T> encoder, T event) {
        return publish(topic, encoder, Mono.just(event));
    }

    Flux<SubscribePayload> subscribe(String topic);

    <T> Mono<Integer> publish(String topic, Publisher<T> event);

    @SuppressWarnings("all")
    default <T> Mono<Integer> publish(String topic, T event) {
        return publish(topic, (Encoder<T>) Codecs.lookup(event.getClass()), event);
    }

}
