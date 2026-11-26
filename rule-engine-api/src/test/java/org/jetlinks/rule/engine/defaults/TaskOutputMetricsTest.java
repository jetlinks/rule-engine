package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.Output;
import org.jetlinks.rule.engine.defaults.scope.InMemoryGlobalScope;
import org.jetlinks.supports.event.InternalEventBus;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class TaskOutputMetricsTest {
    @Test
    public void outputCountsSubscribedDataBeforeFanoutAndExcludesEmptyStreamsAndLifecycleEvents() {
        var bus = new InternalEventBus();
        var context = context(bus);
        var data = RuleData.create("value");
        var write = context.getOutput().write(data);
        assertEquals(0, context.getExecutionMetrics().getOutputCount());
        write.block(); write.block();
        context.getOutput().write(Flux.empty()).block();
        context.getOutput().write(Flux.just(data, data, data)).block();
        context.getOutput().write("other", data).block();
        // 没有目标订阅者时 EventBus 不消费这个 Publisher，未产生的数据不能计数。
        context.getOutput().write("other", Flux.just(data, data)).block();
        context.fireEvent(RuleConstants.Event.complete, data).block();
        assertEquals(6, context.getExecutionMetrics().getOutputCount());
        assertEquals(0, context.getExecutionMetrics().getExecutionCount());
        context.reload();
        assertEquals(6, context.getExecutionMetrics().getOutputCount());
    }

    @Test
    public void oneExecutionCanProduceMultipleOutputsAndVoidSideEffectsProduceNone() {
        var context = context(new InternalEventBus());
        var executor = new FunctionTaskExecutor("test", context) {
            protected Publisher<RuleData> apply(RuleData input) {
                return "empty".equals(input.getData()) ? Mono.empty() : Flux.just(input, input);
            }
        };
        executor.execute(RuleData.create("value")).block();
        executor.execute(RuleData.create("empty")).block();
        assertEquals(2, context.getExecutionMetrics().getExecutionCount());
        assertEquals(2, context.getExecutionMetrics().getOutputCount());
    }

    @Test
    public void streamedOutputPreservesDemandCancellationAndDelegateDisposal() {
        var bus = new InternalEventBus();
        var base = context(bus);
        var disposed = new AtomicBoolean();
        var delegate = new Output() {
            public Mono<Boolean> write(Publisher<RuleData> data) { return Flux.from(data).take(1).then(Mono.just(false)); }
            public Mono<Void> write(String id, Publisher<RuleData> data) { return Flux.from(data).take(1).then(); }
            public void dispose() { disposed.set(true); }
            public boolean isDisposed() { return disposed.get(); }
        };
        var context = new AbstractExecutionContext(base.getJob(), bus,
            job -> org.jetlinks.core.monitor.Monitor.noop(), job -> base.getInput(), job -> delegate,
            job -> Map.of(), job -> new InMemoryGlobalScope()) {};
        assertEquals(Boolean.FALSE, context.getOutput().write(Flux.just(RuleData.create(1), RuleData.create(2))).block());
        assertEquals(1, context.getExecutionMetrics().getOutputCount());
        context.getOutput().dispose();
        assertTrue(context.getOutput().isDisposed()); assertTrue(disposed.get());
    }

    @Test
    public void realEventBusBufferIsScannedFromTheExistingTaskSubscriptionAndDrainsWithoutExtraState() {
        var bus = new InternalEventBus();
        var context = context(bus);
        var gate = Sinks.<Void>empty();
        var executor = new AbstractTaskExecutor(context) {
            public String getName() { return "blocked"; }
            protected Disposable doStart() { return context.getInput().accept().concatMap(data -> gate.asMono(), 0).subscribe(); }
        };
        var task = new DefaultTask("scheduler", "worker", context, executor);
        task.start().block();
        try {
            for (int i = 0; i < 3; i++) bus.publish(RuleConstants.Topics.input("rule", "node"), RuleData.create(i)).block();
            assertEquals(Long.valueOf(2), task.runtime().block().getBuffered());
            gate.tryEmitEmpty();
            assertEquals(Long.valueOf(0), task.runtime().block().getBuffered());
        } finally { task.shutdown().block(); }
        assertNull(DefaultTask.buffered(new Object()));
        Scannable cycle = new Scannable() {
            public Object scanUnsafe(Attr key) { return key == Attr.PARENT ? this : null; }
        };
        assertNull(DefaultTask.buffered(cycle));
    }

    private static DefaultExecutionContext context(InternalEventBus bus) {
        var job = new ScheduleJob(); job.setInstanceId("rule"); job.setNodeId("node"); job.setExecutor("test");
        return new DefaultExecutionContext("worker", job, bus, (condition, data) -> true, new InMemoryGlobalScope());
    }
}
