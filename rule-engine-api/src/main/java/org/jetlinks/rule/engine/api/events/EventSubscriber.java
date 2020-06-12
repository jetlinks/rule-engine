package org.jetlinks.rule.engine.api.events;

import java.util.function.Consumer;

@Deprecated
public interface EventSubscriber {

   <T> void subscribe(Class<T> type, Consumer<T> listener);

}
