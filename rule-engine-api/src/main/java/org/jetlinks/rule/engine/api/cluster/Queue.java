package org.jetlinks.rule.engine.api.cluster;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface Queue<T> {
    boolean poll(Consumer<T> consumer);

    CompletionStage<Boolean> putAsync(T data);

    void put(T data);

    void start();

    void stop();
}
