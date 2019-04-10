package org.jetlinks.rule.engine.api.cluster;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface Queue<T> {
    void accept(Consumer<T> consumer);

    boolean acceptOnce(Consumer<T> consumer);

    CompletionStage<Boolean> putAsync(T data);

    void put(T data);

    void start();

    void stop();
}
