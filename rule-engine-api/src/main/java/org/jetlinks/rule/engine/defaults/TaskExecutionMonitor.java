package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.task.TaskExecutionMetrics;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * 单个任务的内存计数器。用订阅上下文关联同一有限执行的嵌套观察点，避免按输出或恢复链重复计数。
 * 不保存输入，不依赖 Recorder，也不观察常驻订阅的整个生命周期。
 */
final class TaskExecutionMonitor {
    private static final AtomicLongFieldUpdater<TaskExecutionMonitor>
        EXECUTION_COUNT = AtomicLongFieldUpdater.newUpdater(TaskExecutionMonitor.class, "executionCount"),
        ERROR_COUNT = AtomicLongFieldUpdater.newUpdater(TaskExecutionMonitor.class, "errorCount"),
        LAST_EXECUTION_TIME = AtomicLongFieldUpdater.newUpdater(TaskExecutionMonitor.class, "lastExecutionTime");

    private volatile long executionCount;
    private volatile long errorCount;
    private volatile long lastExecutionTime;

    <T> Mono<T> observe(Mono<T> source) {
        return new ExecutionMono<>(source, this);
    }

    void error(ContextView context, Throwable error) {
        ExecutionSubscriber<?> execution = context.getOrDefault(this, null);
        if (execution != null && error != null) {
            execution.fail();
        }
    }

    TaskExecutionMetrics snapshot() {
        TaskExecutionMetrics metrics = new TaskExecutionMetrics();
        metrics.setExecutionCount(executionCount);
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
                EXECUTION_COUNT.incrementAndGet(monitor);
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
