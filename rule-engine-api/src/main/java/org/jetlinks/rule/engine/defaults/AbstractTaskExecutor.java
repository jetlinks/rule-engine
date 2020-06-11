package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.ExecutionContext;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.TaskExecutor;
import reactor.core.Disposable;

import java.util.function.BiConsumer;

@Slf4j
public abstract class AbstractTaskExecutor implements TaskExecutor {

    @Getter
    protected ExecutionContext context;

    @Getter
    protected Task.State state = Task.State.shutdown;

    protected Disposable disposable;

    private BiConsumer<Task.State, Task.State> stateListener = (from, to) -> {
        log.debug("task [{}] state changed from {} to {}.",
                context.getJob(),
                from,
                to);
    };

    public AbstractTaskExecutor(ExecutionContext context) {
        this.context = context;
    }

    @Override
    public abstract String getName();

    protected abstract Disposable doStart();

    protected void changeState(Task.State state) {
        stateListener.accept(this.state, this.state = state);
    }

    @Override
    public void start() {
        if (state == Task.State.running && !disposable.isDisposed()) {
            return;
        }
        disposable = doStart();
        changeState(Task.State.running);
    }

    @Override
    public void reload() {

    }

    @Override
    public void pause() {
        changeState(Task.State.paused);
    }

    @Override
    public void shutdown() {
        if (disposable != null) {
            disposable.dispose();
        }
        changeState(Task.State.shutdown);
    }

    @Override
    public void onStateChanged(BiConsumer<Task.State, Task.State> listener) {
        this.stateListener = this.stateListener.andThen(listener);
    }

    @Override
    public void validate() {

    }
}
