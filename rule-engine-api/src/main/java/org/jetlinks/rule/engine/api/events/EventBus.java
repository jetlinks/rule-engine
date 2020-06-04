package org.jetlinks.rule.engine.api.events;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 基于订阅发布的事件总线
 *
 * <pre>
 *      subscribe("/rule-engine/{rule-id}/{task-id}/input")
 * </pre>
 *
 * @author zhouhao
 * @see 1.0.4
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
    <T> Flux<T> subscribe(String topic, Class<T> type);

    /**
     * 推送事件
     *
     * @param topic       Topic
     * @param eventStream 事件流
     * @return empty Mono
     */
    Mono<Void> publish(String topic, Publisher<?> eventStream);

}
