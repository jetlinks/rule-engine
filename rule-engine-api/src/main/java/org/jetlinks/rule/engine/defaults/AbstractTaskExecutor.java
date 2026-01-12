package org.jetlinks.rule.engine.defaults;

import io.opentelemetry.api.common.AttributeKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.exception.I18nSupportException;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.core.utils.RecursiveUtils;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.ExecutableTaskExecutor;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.Task;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Slf4j
public abstract class AbstractTaskExecutor implements ExecutableTaskExecutor {
    protected final static AttributeKey<String> executor_name = AttributeKey.stringKey("name");

    private static final AtomicReferenceFieldUpdater<AbstractTaskExecutor, Task.State> STATE_UPDATER = AtomicReferenceFieldUpdater
        .newUpdater(AbstractTaskExecutor.class, Task.State.class, "state");

    /**
     * 默认最大递归次数限制.
     * -Drule.engine.max_recursive=0
     */
    protected static final int DEFAULT_MAX_RECURSIVE
        = Integer.getInteger("rule.engine.max_recursive", 0);

    @Getter
    protected ExecutionContext context;

    /**
     * @deprecated {@link AbstractTaskExecutor#getState()}
     */
    @Deprecated
    protected volatile Task.State state = Task.State.initializing;

    private final Disposable.Swap taskContainer = Disposables.swap();

    /**
     * @deprecated {@link AbstractTaskExecutor#startTask()}
     */
    @Deprecated
    protected volatile Disposable disposable;

    protected String operation;

    private volatile BiConsumer<Task.State, Task.State> stateListener =
        AbstractTaskExecutor.log.isDebugEnabled()
            ? (from, to) -> AbstractTaskExecutor.
            log
            .debug("task [{}] state changed from {} to {}.",
                   context.getJob(),
                   from,
                   to)
            : null;

    public AbstractTaskExecutor(ExecutionContext context) {
        this.context = context;
    }

    public Task.State getState() {
        Task.State current = STATE_UPDATER.get(this);
        // 状态是运行中
        if (current == Task.State.running) {
            Disposable task = taskContainer.get();
            // 任务被异常停止了?
            if (task == null || task.isDisposed()) {
                return Task.State.stopped;
            }
            return current;
        }
        return current;
    }

    @SuppressWarnings("all")
    protected <T> MonoTracer<T> tracer() {
        return context
            .monitor()
            .tracer()
            .traceMono("execute");
    }

    @SuppressWarnings("all")
    protected <T> FluxTracer<T> traceFlux() {
        return context
            .monitor()
            .tracer()
            .traceFlux("execute");
    }

    @Override
    public abstract String getName();

    protected abstract Disposable doStart();

    protected void changeState(Task.State state) {
        Task.State oldState = STATE_UPDATER.getAndSet(this, state);
        if (oldState == state) {
            return;
        }
        BiConsumer<Task.State, Task.State> stateListener = this.stateListener;
        if (stateListener != null) {
            stateListener.accept(oldState, state);
        }
    }

    @Override
    public synchronized void start() {
        Disposable disposable = this.disposable;
        // 启动时已经存在任务了?
        if (disposable != null && !disposable.isDisposed()) {
            if (taskContainer.get() != disposable) {
                if (!taskContainer.update(disposable)) {
                    throw new I18nSupportException.NoStackTrace("error.star_task_failed");
                }
            }
            changeState(Task.State.running);
            return;
        }
        if (taskContainer.isDisposed()) {
            throw new I18nSupportException.NoStackTrace("error.task_disposed");
        }
        if (!startTask()) {
            throw new I18nSupportException.NoStackTrace("error.star_task_failed");
        }
        changeState(Task.State.running);
    }

    protected boolean startTask() {
        return taskContainer.update(disposable = doStart());
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
        taskContainer.dispose();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public synchronized void onStateChanged(BiConsumer<Task.State, Task.State> listener) {
        if (stateListener == null) {
            stateListener = listener;
        } else {
            stateListener = stateListener.andThen(listener);
        }
    }

    @Override
    public void validate() {

    }

    @Override
    public Mono<Void> execute(RuleData ruleData) {
        return context
            .getOutput()
            .write(context.newRuleData(ruleData))
            .as(tracer())
            .then();
    }

    protected String operation() {
        String operation = this.operation;
        if (operation == null) {
            synchronized (this) {
                operation = this.operation;
                if (operation == null) {
                    this.operation = operation = "rule:" + context.getInstanceId() + ":" + context.getJob().getNodeId();
                }
            }
        }
        return operation;
    }

    protected Function<reactor.util.context.Context, reactor.util.context.Context> contextWriter() {
        if (maxRecursive() >= 0) {
            return RecursiveUtils
                .validator(operation(), maxRecursive());
        }
        return Function.identity();
    }

    protected int maxRecursive() {
        return DEFAULT_MAX_RECURSIVE;
    }

}
