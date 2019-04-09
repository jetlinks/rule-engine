package org.jetlinks.rule.engine.cluster.redisson;

import org.jetlinks.rule.engine.cluster.Topic;
import org.redisson.api.RTopic;

import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class RedissonTopic<T> implements Topic<T> {

    private RTopic topic;

    private Class<T> type;

    private volatile Consumer<T> consumer = (d) -> {
    };

    public RedissonTopic(RTopic topic, Class<T> type) {
        this.topic = topic;
        this.type = type;
    }

    @Override
    public void addListener(Consumer<T> consumer) {
        this.consumer = this.consumer.andThen(consumer);
    }

    public void start() {
        topic.addListener(type, ((channel, msg) -> consumer.accept(msg)));
    }

    @Override
    public void publish(T data) {
        topic.publish(data);
    }
}
