package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.TaskExecutionMetrics;
import org.jetlinks.rule.engine.defaults.scope.InMemoryGlobalScope;
import org.jetlinks.supports.event.InternalEventBus;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;


import static org.junit.Assert.*;

public class TaskExecutionMetricsTest {
    @Test
    public void finiteSubscriptionsCountEmptyResubscriptionErrorsAndCancellation() {
        var context = context("first", new InternalEventBus());
        Mono<Void> empty = context.observeExecution(Mono.empty());
        assertMetrics(context, 0, 0);
        empty.block();
        empty.block();
        context.observeExecution(context.observeExecution(Mono.empty())).block();
        StepVerifier.create(context.observeExecution(Mono.error(new IllegalStateException("failed"))))
            .expectErrorMessage("failed").verify();
        StepVerifier.create(context.observeExecution(Mono.never())).thenCancel().verify();
        assertMetrics(context, 5, 1);
        TaskExecutionMetrics snapshot = context.getExecutionMetrics();
        context.observeExecution(Mono.empty()).block();
        assertEquals(5, snapshot.getExecutionCount());
        context.reload();
        assertMetrics(context, 6, 1);
        assertMetrics(context("first", new InternalEventBus()), 0, 0);
    }

    @Test
    public void nestedNodesAndRecoveredErrorsRemainIsolatedAcrossAsyncBoundaries() {
        var first = context("first", new InternalEventBus());
        var second = context("second", new InternalEventBus());
        var failure = new IllegalArgumentException("invalid");
        first.observeExecution(second.observeExecution(Mono.empty())
            .then(first.observeExecution(Mono.error(failure))
                .onErrorResume(err -> first.onError(err, null)))
            .subscribeOn(Schedulers.parallel())).block();
        assertMetrics(first, 1, 1);
        assertMetrics(second, 1, 0);
        first.observeExecution(first.onError(failure, null).then(first.onError(failure, null))).block();
        assertMetrics(first, 2, 2);
    }

    @Test
    public void directAndInputExecutionCountOnceIncludingSynchronousAndRecoveredFailures() {
        var bus = new InternalEventBus();
        var context = context("first", bus);
        FunctionTaskExecutor executor = new FunctionTaskExecutor("function", context) {
            protected Publisher<RuleData> apply(RuleData input) {
                if ("throw".equals(input.getData())) throw new IllegalStateException("sync failure");
                return Flux.just(input, input);
            }
        };
        var task = new DefaultTask("scheduler", "worker", context, executor);
        task.execute(RuleData.create("ok")).block();
        task.execute(RuleData.create("throw")).block();
        assertMetrics(context, 2, 1);
        task.start().block();
        bus.publish(RuleConstants.Topics.input("rule", "first"), RuleData.create("ok")).block();
        assertMetrics(context, 3, 1);
        var dump = task.dump().block();
        assertNotNull(dump);
        assertEquals(3, dump.getExecutionMetrics().getExecutionCount());
        task.reload().block();
        assertMetrics(context, 3, 1);
        task.execute(RuleData.create("ok")).block();
        assertEquals(3, dump.getExecutionMetrics().getExecutionCount());
        assertEquals(4, task.dump().block().getExecutionMetrics().getExecutionCount());
        task.shutdown().block();
    }

    @Test
    public void outputFailureIsIncludedBeforeFunctionRecovery() {
        var bus = new InternalEventBus();
        var context = new DefaultExecutionContext("worker", context("first", bus).getJob(), bus,
            (condition, data) -> true, new InMemoryGlobalScope()) {
            @Override
            public org.jetlinks.rule.engine.api.task.Output getOutput() {
                return new org.jetlinks.rule.engine.api.task.Output() {
                    public Mono<Boolean> write(Publisher<RuleData> data) {
                        return Mono.error(new IllegalStateException("output"));
                    }
                    public Mono<Void> write(String node, Publisher<RuleData> data) {
                        return Mono.error(new IllegalStateException("output"));
                    }
                };
            }
        };
        var executor = new FunctionTaskExecutor("function", context) {
            protected Publisher<RuleData> apply(RuleData input) { return Mono.just(input); }
        };
        executor.execute(RuleData.create("value")).block();
        assertMetrics(context, 1, 1);
    }

    @Test
    public void inputDecorationPreservesCustomAcknowledgmentsAndDisposal() {
        var bus = new InternalEventBus();
        var original = context("first", bus);
        var disposed = new java.util.concurrent.atomic.AtomicBoolean();
        Flux<RuleData> raw = Flux.empty();
        var input = new org.jetlinks.rule.engine.api.task.Input() {
            public Flux<RuleData> accept() { return raw; }
            public reactor.core.Disposable accept(java.util.function.Function<RuleData, Mono<Boolean>> listener) {
                StepVerifier.create(listener.apply(RuleData.create("reject"))).expectNext(false).verifyComplete();
                StepVerifier.create(listener.apply(RuleData.create("throw"))).expectErrorMessage("sync").verify();
                return reactor.core.Disposables.single();
            }
            public void dispose() { disposed.set(true); }
            public boolean isDisposed() { return disposed.get(); }
        };
        var context = new AbstractExecutionContext(original.getJob(), bus,
            job -> org.jetlinks.core.monitor.Monitor.noop(), job -> input,
            job -> original.getOutput(), job -> java.util.Map.of(), job -> new InMemoryGlobalScope()) {};
        assertSame(raw, context.getInput().accept());
        var subscription = context.getInput().accept(data -> {
            if ("throw".equals(data.getData())) throw new IllegalStateException("sync");
            return Mono.just(false);
        });
        assertMetrics(context, 2, 1);
        context.onError(new IllegalStateException("outside execution"), null).block();
        context.observeExecution(context.onError((Throwable) null, null)).block();
        assertMetrics(context, 3, 1);
        assertFalse(context.getInput().isDisposed());
        context.getInput().dispose();
        assertTrue(context.getInput().isDisposed());
        subscription.dispose();
    }

    @Test
    public void concurrentExecutionsDoNotLoseCounts() {
        var context = context("first", new InternalEventBus());
        Flux.range(0, 1000).flatMap(i -> context.observeExecution(Mono.empty())
            .subscribeOn(Schedulers.parallel()), 32).blockLast();
        assertMetrics(context, 1000, 0);
    }

    private static DefaultExecutionContext context(String node, InternalEventBus bus) {
        var job = new ScheduleJob();
        job.setInstanceId("rule");
        job.setNodeId(node);
        job.setExecutor("function");
        return new DefaultExecutionContext("worker", job, bus, (condition, data) -> true, new InMemoryGlobalScope());
    }

    private static void assertMetrics(AbstractExecutionContext context, long count, long errors) {
        var metrics = context.getExecutionMetrics();
        assertEquals(count, metrics.getExecutionCount());
        assertEquals(errors, metrics.getErrorCount());
        if (count > 0) assertTrue(metrics.getLastExecutionTime() > 0);
        else assertEquals(0, metrics.getLastExecutionTime());
    }
}
