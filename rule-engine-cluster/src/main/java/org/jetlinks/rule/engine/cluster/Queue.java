package org.jetlinks.rule.engine.cluster;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface Queue<T> {
    void accept(Consumer<T> consumer);

    boolean acceptOnce(Consumer<T> consumer);

    CompletionStage<Void> putAsync(T data);
}
