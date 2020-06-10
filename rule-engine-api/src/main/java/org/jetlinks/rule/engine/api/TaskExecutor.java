package org.jetlinks.rule.engine.api;

import java.util.function.BiConsumer;

public interface TaskExecutor {

    String getName();

    void start();

    void reload();

    void pause();

    void shutdown();

    Task.State getState();

    void onStateChanged(BiConsumer<Task.State, Task.State> listener);
}
