package org.jetlinks.rule.engine.defaults;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.lang.SharedPathString;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.core.utils.RecursiveUtils;
import org.jetlinks.core.utils.RecyclerUtils;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.ExecutableTaskExecutor;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.Task;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.function.BiConsumer;
import java.util.function.Function;

@Slf4j
public abstract class AbstractTaskExecutor implements ExecutableTaskExecutor {
    protected final static AttributeKey<String> executor_name = AttributeKey.stringKey("name");

    /**
     * 默认最大递归次数限制.
     * -Drule.engine.max_recursive=0
     */
    protected static final int DEFAULT_MAX_RECURSIVE = Integer.getInteger("rule.engine.max_recursive", 0);

    @Getter
    protected ExecutionContext context;

    @Getter
    protected volatile Task.State state = Task.State.shutdown;

    protected volatile Disposable disposable;
    private MonoTracer<Object> tracer;
    private FluxTracer<Object> fluxTracer;

    private BiConsumer<Task.State, Task.State> stateListener = (from, to) -> {
        AbstractTaskExecutor.log.debug("task [{}] state changed from {} to {}.",
                                       context.getJob(),
                                       from,
                                       to);
    };

    public AbstractTaskExecutor(ExecutionContext context) {
        this.context = context;
    }

    protected CharSequence createSpanName() {
        return SharedPathString
            .of(new String[]{
                "",
                "rule-runtime",
                RecyclerUtils.intern(context.getJob().getExecutor()),
                RecyclerUtils.intern(context.getInstanceId()),
                RecyclerUtils.intern(context.getJob().getNodeId())
            });
    }

    protected <T> MonoTracer<T> createMonoTracer() {
        return MonoTracer
            .<T>builder()
            .spanName(createSpanName())
            .onSubscription(builder -> builder.setAttribute(executor_name, this.getName()))
            .defaultContext(Context::root)
            .build();
    }

    protected <T> FluxTracer<T> createFluxTracer() {
        return FluxTracer
            .<T>builder()
            .spanName(createSpanName())
            .onSubscription(builder -> builder.setAttribute(executor_name, this.getName()))
            .defaultContext(Context::root)
            .build();
    }

    @SuppressWarnings("all")
    protected <T> MonoTracer<T> tracer() {
        MonoTracer<Object> _tracer = this.tracer;
        return (MonoTracer) (_tracer == null ? _tracer = tracer = createMonoTracer() : _tracer);
    }

    @SuppressWarnings("all")
    protected <T> FluxTracer<T> traceFlux() {
        FluxTracer<Object> _tracer = this.fluxTracer;
        return (FluxTracer) (_tracer == null ? _tracer = fluxTracer = createFluxTracer() : _tracer);
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
            .write(context.newRuleData(ruleData))
            .as(tracer())
            .then();
    }

    protected Function<reactor.util.context.Context, reactor.util.context.Context> contextWriter() {
        if (maxRecursive() >= 0) {
            return RecursiveUtils
                .validator(
                    "rule:" + context.getInstanceId() + ":" + context.getJob().getNodeId(),
                    maxRecursive());
        }
        return Function.identity();
    }

    protected int maxRecursive() {
        return DEFAULT_MAX_RECURSIVE;
    }

}
