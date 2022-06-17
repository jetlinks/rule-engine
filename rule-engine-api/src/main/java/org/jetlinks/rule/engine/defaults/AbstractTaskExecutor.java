package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.ExecutableTaskExecutor;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.Task;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.function.BiConsumer;

@Slf4j
public abstract class AbstractTaskExecutor implements ExecutableTaskExecutor {

    @Getter
    protected ExecutionContext context;

    @Getter
    protected volatile Task.State state = Task.State.shutdown;

    protected volatile Disposable disposable;

    private BiConsumer<Task.State, Task.State> stateListener = (from, to) -> {
        AbstractTaskExecutor.log.debug("task [{}] state changed from {} to {}.",
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
        if (this.state == state) {
            return;
        }
        stateListener.accept(this.state, this.state = state);
    }

    @Override
    public synchronized void start() {
        if (disposable != null && !disposable.isDisposed()) {
            changeState(Task.State.running);
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
    public synchronized void shutdown() {
        changeState(Task.State.shutdown);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public void onStateChanged(BiConsumer<Task.State, Task.State> listener) {
        this.stateListener = this.stateListener.andThen(listener);
    }

    @Override
    public void validate() {

    }

    @Override
    public Mono<Void> execute(RuleData ruleData) {
        return context
                .getOutput()
                .write(ruleData)
                .then()
                ;
    }
}
