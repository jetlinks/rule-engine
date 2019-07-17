package org.jetlinks.rule.engine.api.events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class DefaultEventPubSub implements EventPublisher, EventSubscriber {

    public static final DefaultEventPubSub GLOBAL = new DefaultEventPubSub();


    protected Map<Class, List<Consumer>> listeners = new ConcurrentHashMap<>();

    @Override
    public void publishEvent(Object event) {
        getListeners(event.getClass())
                .forEach(consumer -> consumer.accept(event));
    }

    @Override
    public <T> void subscribe(Class<T> type, Consumer<T> listener) {
        getListeners(type).add(listener);
    }

    protected List<Consumer> getListeners(Class type) {
        return listeners.computeIfAbsent(type, __ -> new CopyOnWriteArrayList<>());
    }
}
