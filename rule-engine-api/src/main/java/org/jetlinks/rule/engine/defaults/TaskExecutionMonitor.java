package org.jetlinks.rule.engine.defaults;

import org.jetlinks.core.monitor.metrics.RateMeter;
import org.jetlinks.rule.engine.api.task.TaskExecutionMetrics;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;

/**
 * 单个任务的内存计数器。用订阅上下文关联同一有限执行的嵌套观察点，避免按输出或恢复链重复计数。
 * 不保存输入，不依赖 Recorder，也不观察常驻订阅的整个生命周期。
 */
final class TaskExecutionMonitor extends RateMeter {
    private static final AtomicLongFieldUpdater<TaskExecutionMonitor>
        ERROR_COUNT = AtomicLongFieldUpdater.newUpdater(TaskExecutionMonitor.class, "errorCount"),
        LAST_EXECUTION_TIME = AtomicLongFieldUpdater.newUpdater(TaskExecutionMonitor.class, "lastExecutionTime");

    private volatile long errorCount;
    private volatile long lastExecutionTime;

    @SuppressWarnings("unchecked")
    <T> Mono<T> observe(Mono<T> source) {
        if (source instanceof DeferredExecutionMono<?, ?> deferred && deferred.monitor == null) {
            // 载体的返回类型由 Mono<T> 保证；融合时仍保留原 observeExecution 扩展入口。
            return ((DeferredExecutionMono<?, T>) deferred).observe(this);
        }
        return new ExecutionMono<>(source, this);
    }

    static <I, T> Mono<T> defer(I input, Function<? super I, ? extends Mono<? extends T>> action) {
        return new DeferredExecutionMono<>(input, action, null);
    }

    void error(ContextView context, Throwable error) {
        ExecutionSubscriber<?> execution = context.getOrDefault(this, null);
        if (execution != null && error != null) {
            execution.fail();
        }
    }

    TaskExecutionMetrics snapshot() {
        TaskExecutionMetrics metrics = new TaskExecutionMetrics();
        metrics.setExecutionCount(getCount());
        metrics.setMeanExecutionRate(getMeanRate());
        metrics.setOneMinuteExecutionRate(getOneMinuteRate());
        metrics.setErrorCount(errorCount);
        metrics.setLastExecutionTime(lastExecutionTime);
        return metrics;
    }

    /** 组装时只创建一个操作符，有限执行状态在每次订阅内创建。 */
    private static final class ExecutionMono<T> extends MonoOperator<T, T> {
        private final TaskExecutionMonitor monitor;

        private ExecutionMono(Mono<? extends T> source, TaskExecutionMonitor monitor) {
            super(source);
            this.monitor = monitor;
        }

        @Override
        public void subscribe(@Nonnull CoreSubscriber<? super T> actual) {
            source.subscribe(new ExecutionSubscriber<>(actual, monitor));
        }
    }

    /** 输入回调按订阅求值；统计入口融合后直接订阅业务 Mono，不保留额外 defer 层。 */
    private static final class DeferredExecutionMono<I, T> extends Mono<T> {
        private final I input;
        private final Function<? super I, ? extends Mono<? extends T>> action;
        private final TaskExecutionMonitor monitor;

        private DeferredExecutionMono(I input,
                                      Function<? super I, ? extends Mono<? extends T>> action,
                                      TaskExecutionMonitor monitor) {
            this.input = input;
            this.action = action;
            this.monitor = monitor;
        }

        private Mono<T> observe(TaskExecutionMonitor monitor) {
            return new DeferredExecutionMono<>(input, action, monitor);
        }

        @Override
        public void subscribe(@Nonnull CoreSubscriber<? super T> actual) {
            CoreSubscriber<? super T> subscriber = monitor == null
                ? actual : new ExecutionSubscriber<>(actual, monitor);
            Mono<? extends T> source;
            try {
                source = Objects.requireNonNull(action.apply(input), "The execution callback returned null");
            } catch (Throwable error) {
                Operators.error(subscriber, Operators.onOperatorError(error, subscriber.currentContext()));
                return;
            }
            Mono.fromDirect(source).subscribe(subscriber);
        }
    }

    /**
     * 根观察点的订阅器同时保存本次执行状态，嵌套点共享根状态；不额外分配 Sample 或原子对象。
     * BaseSubscriber 负责请求、取消及终止保护，只在恢复前的错误通道补充计数。
     */
    private static final class ExecutionSubscriber<T> extends BaseSubscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<ExecutionSubscriber> FAILED =
            AtomicIntegerFieldUpdater.newUpdater(ExecutionSubscriber.class, "failed");

        private final CoreSubscriber<? super T> actual;
        private final TaskExecutionMonitor monitor;
        private final ExecutionSubscriber<?> execution;
        private final Context context;
        private volatile int failed;

        private ExecutionSubscriber(CoreSubscriber<? super T> actual, TaskExecutionMonitor monitor) {
            this.actual = actual;
            this.monitor = monitor;
            Context inheritedContext = actual.currentContext();
            ExecutionSubscriber<?> inherited = inheritedContext.getOrDefault(monitor, null);
            if (inherited == null) {
                execution = this;
                context = inheritedContext.put(monitor, this);
                monitor.mark();
                // 并发执行开始时间可能乱序到达，不能让较早时间覆盖较晚时间。
                LAST_EXECUTION_TIME.accumulateAndGet(monitor, System.currentTimeMillis(), Math::max);
            } else {
                execution = inherited;
                context = inheritedContext;
            }
        }

        private void fail() {
            if (FAILED.compareAndSet(this, 0, 1)) {
                ERROR_COUNT.incrementAndGet(monitor);
            }
        }

        @Override
        @Nonnull
        public Context currentContext() {
            return context;
        }

        @Override
        protected void hookOnSubscribe(@Nonnull Subscription subscription) {
            actual.onSubscribe(this);
        }

        @Override
        protected void hookOnNext(@Nonnull T value) {
            actual.onNext(value);
        }

        @Override
        protected void hookOnError(@Nonnull Throwable error) {
            // 嵌套操作的错误可能随即恢复，仍须在向下游传递前标记本次根执行失败。
            execution.fail();
            actual.onError(error);
        }

        @Override
        protected void hookOnComplete() {
            actual.onComplete();
        }
    }
}
