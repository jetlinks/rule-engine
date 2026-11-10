package org.jetlinks.rule.engine.defaults;

import io.opentelemetry.api.common.AttributeKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.exception.I18nSupportException;
import org.jetlinks.core.monitor.observation.ObservationSpec;
import org.jetlinks.core.monitor.observation.Observations;
import org.jetlinks.core.monitor.recorder.ActionRecorder;
import org.jetlinks.core.monitor.recorder.Recorder;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.core.utils.RecursiveUtils;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.ExecutableTaskExecutor;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.Task;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 规则任务的公共生命周期、递归保护和可选执行观测。
 * 观测只装饰一次订阅，不负责业务重试；子类负责输入调度和输出分发。
 *
 * @see FunctionTaskExecutor
 */
@Slf4j
public abstract class AbstractTaskExecutor implements ExecutableTaskExecutor {
    private static final ObservationSpec<RuleData, RuleData> EXECUTION = ObservationSpec.of("execute");
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
        return observe(context.newRuleData(ruleData), Flux::just)
            .concatMap(context.getOutput()::write, 0)
            .then()
            .as(tracer());
    }

    /**
     * 是否启用公共观测。已有手工埋点的子类保持关闭，迁移后再开启，避免重复记录。
     * 此配置在组装时读取；业务与动作生命周期仍在每次订阅内执行。
     *
     * @return 是否启用；默认 false
     * @since 1.3.3
     */
    protected boolean observeExecution() {
        return false;
    }

    /**
     * 公共观察点使用的不可变配置，默认只记录 execute 生命周期。
     * 组装时读取；提取回调仅在详细采集开启后执行，不额外订阅或查询业务数据。
     *
     * @return 可复用的非空配置；输出不代表持久化或业务成功
     * @since 1.3.3
     * @see ObservationSpec
     */
    protected ObservationSpec<RuleData, RuleData> observationSpec() {
        return EXECUTION;
    }

    /**
     * 为原生动作补充标签，在动作启动前调用；不负责 start/complete/error。
     *
     * @param recorder 本次未启动的原生动作
     * @param input 当前输入，不应修改或长期保存
     * @since 1.3.3
     */
    protected void tagObservation(ActionRecorder<?> recorder, RuleData input) {
        recorder.tag(RuleConstants.Tags.contextId, input.getContextId());
    }

    /**
     * 在观测选择前恢复本次订阅上下文，默认保留调用者上下文。
     * 子类只适配输入载体，不在此订阅、执行 I/O 或保存请求状态；异常终止本次执行。
     *
     * @param context 当前订阅上下文
     * @param input 本次输入
     * @return 非空上下文；输入身份不能从未受信任的任意字段推断
     * @since 1.3.3
     */
    protected Context prepareContext(Context context, RuleData input) {
        return context;
    }

    /**
     * 在公共边界执行一次输入。operation 在每次订阅时调用，取消和异常沿原流传递。
     * Observations 已提供惰性边界，启用路径无需再次包装 defer。
     *
     * @param input 当前规则输入
     * @param operation 原业务多值操作，不得自行订阅或返回 null
     * @return 保持业务信号和背压的流；禁用观测时仅执行业务
     * @since 1.3.3
     */
    protected final Flux<RuleData> observe(RuleData input, Function<RuleData, Flux<RuleData>> operation) {
        if (!observeExecution()) {
            return Flux.defer(() -> operation.apply(input))
                .contextWrite(ctx -> prepareContext(ctx, input));
        }
        Recorder recorder = observationRecorder(input);
        return Observations.observeFlux(recorder, observationSpec(), input, operation)
            .contextWrite(ctx -> prepareContext(ctx, input).put(Recorder.class, recorder));
    }

    private Recorder observationRecorder(RuleData input) {
        Recorder original = context.monitor().recorder();
        if (original == Recorder.noop()) {
            return original;
        }
        return new Recorder() {
            @Override
            public <E> ActionRecorder<E> action(CharSequence action) {
                ActionRecorder<E> recorder = original.action(action);
                tagObservation(recorder, input);
                return recorder;
            }
        };
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
