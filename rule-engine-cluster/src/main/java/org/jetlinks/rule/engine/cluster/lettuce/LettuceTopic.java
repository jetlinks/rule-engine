package org.jetlinks.rule.engine.cluster.lettuce;

import org.jetlinks.lettuce.RedisTopic;
import org.jetlinks.rule.engine.api.cluster.Topic;

import java.util.function.Consumer;

public class LettuceTopic<T> implements Topic<T> {

    private RedisTopic<T> topic;

    private Class<T> type;

    public LettuceTopic(Class<T> type, RedisTopic<T> topic) {
        this.topic = topic;
        this.type = type;
    }

    @Override
    public void addListener(Consumer<T> consumer) {
        topic.addListener((channel, data) -> consumer.accept(convert(data)));
    }

    @Override
    public void publish(T data) {
        topic.publish(data);
    }

    @SuppressWarnings("all")
    protected T convert(Object data) {
        // TODO: 2019-07-05
        return ((T) data);
    }
}
