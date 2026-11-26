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
    public void terminalOutputCountsCallsAndConsumedStreamsAndExcludesLifecycleEvents() {
        var bus = new InternalEventBus();
        var context = context(bus);
        var data = RuleData.create("value");
        var write = context.getOutput().write(data);
        assertEquals(1, context.getOutput().getMetrics().getCount());
        write.block(); write.block();
        assertEquals(1, context.getOutput().getMetrics().getCount());
        context.getOutput().write(Flux.empty()).block();
        context.getOutput().write(Flux.just(data, data, data)).block();
        context.getOutput().write("other", data).block();
        // 没有目标订阅者时 EventBus 不消费这个 Publisher，未产生的数据不能计数。
        context.getOutput().write("other", Flux.just(data, data)).block();
        context.fireEvent(RuleConstants.Event.complete, data).block();
        assertEquals(5, context.getOutput().getMetrics().getCount());
        assertEquals(0, context.getExecutionMetrics().getExecutionCount());
        var previous = context.getOutput();
        context.reload();
        assertEquals(0, context.getOutput().getMetrics().getCount());
        assertEquals(5, previous.getMetrics().getCount());
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
        assertEquals(2, context.getOutput().getMetrics().getCount());
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
        assertSame(delegate, context.getOutput());
        assertNull(context.getOutput().getMetrics());
        context.getOutput().dispose();
        assertTrue(context.getOutput().isDisposed()); assertTrue(disposed.get());
    }

    @Test
    public void outputOwnsMetricsWithoutExecutionContextAndCountsBroadcastOnce() {
        var bus = new InternalEventBus();
        var received = new java.util.concurrent.atomic.AtomicInteger();
        var left = bus.subscribe(org.jetlinks.core.event.Subscription.of("left", RuleConstants.Topics.input("rule", "left"), org.jetlinks.core.event.Subscription.Feature.local), RuleData.class)
            .subscribe(data -> received.incrementAndGet());
        var right = bus.subscribe(org.jetlinks.core.event.Subscription.of("right", RuleConstants.Topics.input("rule", "right"), org.jetlinks.core.event.Subscription.Feature.local), RuleData.class)
            .subscribe(data -> received.incrementAndGet());
        var output = new EventBusOutput("rule", bus, java.util.List.of(
            new ScheduleJob.Output("left", null), new ScheduleJob.Output("right", null)), (condition, data) -> true);
        try {
            var before = output.getMetrics();
            var write = output.write(RuleData.create("value"));
            assertEquals(0, output.getMetrics().getCount());
            write.block();
            assertEquals(2, received.get());
            assertEquals(1, output.getMetrics().getCount());
            assertEquals(0, before.getCount());
            var metadata = org.jetlinks.core.monitor.metrics.MetricSet.of(org.jetlinks.rule.engine.api.task.OutputMetrics.class);
            assertEquals(2, metadata.getMetrics().size());
        } finally { left.dispose(); right.dispose(); }
    }

    @Test
    public void terminalOutputReusesTheSharedResultAndCountsConcurrentWriteCalls() {
        var output = new EventBusOutput("rule", new InternalEventBus(), null, null);
        var data = RuleData.create("value");
        assertSame(org.jetlinks.core.utils.Reactors.ALWAYS_TRUE, output.write(data));
        Flux.range(0, 2000).parallel(4).runOn(reactor.core.scheduler.Schedulers.parallel())
            .flatMap(ignore -> output.write(data)).sequential().blockLast();
        assertEquals(2001, output.getMetrics().getCount());
    }

    @Test
    public void conditionalOutputsDeliverOnlyMatchingDataAndCountBeforeFiltering() {
        var bus = new InternalEventBus();
        var received = new java.util.concurrent.CopyOnWriteArrayList<Object>();
        var subscription = bus.subscribe(org.jetlinks.core.event.Subscription.of("target",
                RuleConstants.Topics.input("rule", "target"), org.jetlinks.core.event.Subscription.Feature.local), RuleData.class)
            .subscribe(data -> received.add(data.getData()));
        var condition = new org.jetlinks.rule.engine.api.model.Condition();
        var evaluations = new java.util.concurrent.atomic.AtomicInteger();
        var output = new EventBusOutput("rule", bus, java.util.List.of(new ScheduleJob.Output("target", condition)),
            (actual, data) -> {
                assertSame(condition, actual);
                evaluations.incrementAndGet();
                return "accept".equals(data.getData());
            });
        try {
            assertEquals(Boolean.TRUE, output.write(RuleData.create("accept")).block());
            assertEquals(Boolean.FALSE, output.write(RuleData.create("reject")).block());
            assertEquals(java.util.List.of("accept"), received);
            assertEquals(2, evaluations.get());
            assertEquals(2, output.getMetrics().getCount());
        } finally { subscription.dispose(); }
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
