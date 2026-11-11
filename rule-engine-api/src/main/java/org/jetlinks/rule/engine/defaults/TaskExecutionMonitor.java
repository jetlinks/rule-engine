package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.task.TaskExecutionMetrics;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单个任务的内存计数器。用订阅上下文关联同一有限执行的嵌套观察点，避免按输出或恢复链重复计数。
 * 不保存输入，不依赖 Recorder，也不观察常驻订阅的整个生命周期。
 */
final class TaskExecutionMonitor {
    private final AtomicLong executionCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong lastExecutionTime = new AtomicLong();

    <T> Mono<T> observe(Mono<T> source) {
        return Mono.deferContextual(ctx -> {
            Sample inherited = ctx.getOrDefault(this, null);
            if (inherited != null) {
                return source.doOnError(inherited::fail);
            }
            Sample sample = new Sample();
            executionCount.incrementAndGet();
            lastExecutionTime.accumulateAndGet(System.currentTimeMillis(), Math::max);
            return source.doOnError(sample::fail)
                .contextWrite(context -> context.put(this, sample));
        });
    }

    void error(ContextView context, Throwable error) {
        Sample sample = context.getOrDefault(this, null);
        if (sample != null && error != null) {
            sample.fail(error);
        }
    }

    TaskExecutionMetrics snapshot() {
        TaskExecutionMetrics metrics = new TaskExecutionMetrics();
        metrics.setExecutionCount(executionCount.get());
        metrics.setErrorCount(errorCount.get());
        metrics.setLastExecutionTime(lastExecutionTime.get());
        return metrics;
    }

    private final class Sample {
        private final AtomicBoolean failed = new AtomicBoolean();

        void fail(Throwable error) {
            if (failed.compareAndSet(false, true)) {
                errorCount.incrementAndGet();
            }
        }
    }
}
