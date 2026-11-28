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
        if (source instanceof ExecutionMono<?> observed && observed.monitor == this) return source;
        if (source instanceof DeferredExecutionMono<?, ?> deferred) {
            if (deferred.monitor == this) return source;
            if (deferred.monitor == null) {
                // 载体的返回类型由 Mono<T> 保证；融合时仍保留原 observeExecution 扩展入口。
                return ((DeferredExecutionMono<?, T>) deferred).observe(this);
            }
        }
        return new ExecutionMono<>(source, this);
    }

    static <I, T> Mono<T> defer(I input, Function<? super I, ? extends Mono<? extends T>> action) {
        return new DeferredExecutionMono<>(input, action, null);
    }

    void error(ContextView context, Throwable error) {
        RootExecutionSubscriber<?> execution = context.getOrDefault(this, null);
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

    private <T> CoreSubscriber<? super T> subscriber(CoreSubscriber<? super T> actual) {
        // 直接相邻的观察点复用原订阅器；隔着恢复等算子时仍须观察原始错误。
        if (actual instanceof ExecutionSubscriber<?> observed && observed.root().monitor == this) return actual;
        Context context = actual.currentContext();
        RootExecutionSubscriber<?> execution = context.getOrDefault(this, null);
        return execution == null ? new RootExecutionSubscriber<>(actual, this, context)
            : new NestedExecutionSubscriber<>(actual, execution, context);
    }

    private static <T> void subscribeSource(Mono<? extends T> source, CoreSubscriber<? super T> subscriber) {
        try {
            source.subscribe(subscriber);
        } catch (Throwable error) {
            Operators.reportThrowInSubscribe(subscriber, error);
        }
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
            subscribeSource(source, monitor.subscriber(actual));
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
                ? actual : monitor.subscriber(actual);
            Mono<? extends T> source;
            try {
                source = Objects.requireNonNull(action.apply(input), "The execution callback returned null");
            } catch (Throwable error) {
                Operators.error(subscriber, Operators.onOperatorError(error, subscriber.currentContext()));
                return;
            }
            subscribeSource(Mono.fromDirect(source), subscriber);
        }
    }

    /**
     * 共同保留请求、取消及终止保护；恢复前的错误由根执行去重，不额外分配 Sample 或原子对象。
     */
    private abstract static class ExecutionSubscriber<T> extends BaseSubscriber<T> {
        private final CoreSubscriber<? super T> actual;

        private ExecutionSubscriber(CoreSubscriber<? super T> actual) {
            this.actual = actual;
        }

        abstract RootExecutionSubscriber<?> root();

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
            root().fail();
            actual.onError(error);
        }

        @Override
        protected void hookOnComplete() {
            actual.onComplete();
        }
    }

    private static final class RootExecutionSubscriber<T> extends ExecutionSubscriber<T> {
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<RootExecutionSubscriber> FAILED =
            AtomicIntegerFieldUpdater.newUpdater(RootExecutionSubscriber.class, "failed");

        private final TaskExecutionMonitor monitor;
        private final Context context;
        private volatile int failed;

        private RootExecutionSubscriber(CoreSubscriber<? super T> actual, TaskExecutionMonitor monitor, Context inherited) {
            super(actual);
            this.monitor = monitor;
            this.context = inherited.put(monitor, this);
            monitor.mark();
            // 并发执行开始时间可能乱序到达，不能让较早时间覆盖较晚时间。
            LAST_EXECUTION_TIME.accumulateAndGet(monitor, System.currentTimeMillis(), Math::max);
        }

        private void fail() {
            if (FAILED.compareAndSet(this, 0, 1)) ERROR_COUNT.incrementAndGet(monitor);
        }

        @Override
        RootExecutionSubscriber<?> root() { return this; }

        @Override
        @Nonnull
        public Context currentContext() { return context; }
    }

    private static final class NestedExecutionSubscriber<T> extends ExecutionSubscriber<T> {
        private final RootExecutionSubscriber<?> execution;
        private final Context context;

        private NestedExecutionSubscriber(CoreSubscriber<? super T> actual, RootExecutionSubscriber<?> execution, Context context) {
            super(actual);
            this.execution = execution;
            this.context = context;
        }

        @Override
        RootExecutionSubscriber<?> root() { return execution; }

        @Override
        @Nonnull
        public Context currentContext() { return context; }
    }
}
