package org.jetlinks.rule.engine.cluster;

import java.util.function.Consumer;

public interface Queue<T> {
    void accept(Consumer<T> consumer);

    boolean acceptOnce(Consumer<T> consumer);

    void put(T data);
}
