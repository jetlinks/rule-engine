package org.jetlinks.rule.engine.defaults;

import org.jetlinks.core.metadata.UnitSupported;
import org.jetlinks.core.monitor.metrics.MetricKind;
import org.jetlinks.core.monitor.metrics.MetricSet;
import org.jetlinks.rule.engine.api.task.TaskExecutionMetrics;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;
import reactor.util.context.Context;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TaskExecutionMonitorTest {
    @Test
    public void unusedMonitorHasZeroRatesAndReadingDoesNotResetCounts() {
        var monitor = new TaskExecutionMonitor();
        var unused = monitor.snapshot();
        assertEquals(0, unused.getExecutionCount());
        assertEquals(0, unused.getMeanExecutionRate(), 0);
        assertEquals(0, unused.getOneMinuteExecutionRate(), 0);
        Mono<Void> execution = monitor.observe(monitor.observe(Mono.empty()));
        execution.block();
        execution.block();
        assertMetrics(monitor, 2, 0);
        assertMetrics(monitor, 2, 0);
    }

    @Test
    public void publicSnapshotExposesRateSemanticsAndValues() throws Throwable {
        var definition = MetricSet.of(TaskExecutionMetrics.class);
        assertSame(definition, MetricSet.of(TaskExecutionMetrics.class));
        assertEquals(5, definition.getMetrics().size());
        var snapshot = new TaskExecutionMetrics();
        snapshot.setMeanExecutionRate(2.5);
        snapshot.setOneMinuteExecutionRate(3.5);
        var mean = definition.getMetrics().stream()
            .filter(metric -> metric.getId().equals("meanExecutionRate")).findFirst().orElseThrow();
        var smoothed = definition.getMetrics().stream()
            .filter(metric -> metric.getId().equals("oneMinuteExecutionRate")).findFirst().orElseThrow();
        assertEquals(MetricKind.gauge, mean.getKind());
        assertEquals(2.5, (Double) mean.read(snapshot), 0);
        assertEquals(3.5, (Double) smoothed.read(snapshot), 0);
        var meanSemantics = (Map<?, ?>) mean.getMetadata().getExpand("metric").orElseThrow();
        var smoothedSemantics = (Map<?, ?>) smoothed.getMetadata().getExpand("metric").orElseThrow();
        assertEquals("mean", meanSemantics.get("aggregation"));
        assertEquals("lifetime", meanSemantics.get("scope"));
        assertEquals("ewma", smoothedSemantics.get("aggregation"));
        assertEquals("exponential", smoothedSemantics.get("scope"));
        assertEquals(60L, smoothedSemantics.get("timeConstantSeconds"));
        assertEquals(5L, smoothedSemantics.get("tickSeconds"));
        assertEquals("count/s", ((UnitSupported) mean.getMetadata().getValueType()).getUnit().getId());
    }

    @Test
    public void preservesContextAndDownstreamDemand() {
        var monitor = new TaskExecutionMonitor();
        var publisher = TestPublisher.<Integer>createCold();
        Mono<Integer> source = Mono.deferContextual(ctx -> {
            assertEquals("tenant-a", ctx.get("tenant"));
            return publisher.mono();
        });
        StepVerifier.create(monitor.observe(source).contextWrite(Context.of("tenant", "tenant-a")), 0)
            .expectSubscription()
            .then(() -> publisher.assertMaxRequested(0))
            .thenRequest(1)
            .then(() -> publisher.assertMinRequested(1))
            .then(() -> publisher.emit(42))
            .expectNext(42)
            .verifyComplete();
        assertMetrics(monitor, 1, 0);
    }

    @Test
    public void cancellationReachesSourceWithoutCountingFailure() {
        var monitor = new TaskExecutionMonitor();
        var publisher = TestPublisher.<Integer>create();
        StepVerifier.create(monitor.observe(publisher.mono()), 0)
            .expectSubscription().thenCancel().verify();
        publisher.assertCancelled();
        assertMetrics(monitor, 1, 0);
    }

    @Test
    public void nestedErrorIsCountedEvenWhenRecoveredOutsideTheNestedObserver() {
        var monitor = new TaskExecutionMonitor();
        Mono<Void> nested = monitor.observe(Mono.error(new IllegalStateException("nested")));
        Mono<Void> execution = monitor.observe(TaskExecutionMonitor.defer(nested, source -> source.onErrorResume(error -> Mono.empty())));
        execution.block();
        execution.block();
        assertMetrics(monitor, 2, 2);
    }

    @Test
    public void adjacentObserversAreIdempotentWithinTheSameMonitor() {
        var monitor = new TaskExecutionMonitor();
        Mono<Integer> direct = monitor.observe(Mono.just(42));
        Mono<Integer> deferred = monitor.observe(TaskExecutionMonitor.defer(42, Mono::just));
        assertSame(direct, monitor.observe(direct));
        assertSame(deferred, monitor.observe(deferred));
        assertEquals(Integer.valueOf(42), direct.block());
        assertEquals(Integer.valueOf(42), deferred.block());
        assertMetrics(monitor, 2, 0);
    }

    @Test
    public void deferredCallbacksRunPerSubscriptionAndKeepContextAndValues() {
        var monitor = new TaskExecutionMonitor();
        var calls = new AtomicInteger();
        Mono<Integer> execution = monitor.observe(TaskExecutionMonitor.defer(42, input -> {
            calls.incrementAndGet();
            return monitor.observe(Mono.deferContextual(context -> {
                assertEquals("tenant-a", context.get("tenant"));
                return Mono.just(input);
            }));
        }));
        assertEquals(0, calls.get());
        assertEquals(0, monitor.getCount());
        StepVerifier.create(execution.contextWrite(Context.of("tenant", "tenant-a")), 0)
            .expectSubscription()
            .then(() -> assertEquals(1, calls.get()))
            .thenRequest(1).expectNext(42).verifyComplete();
        assertEquals(Integer.valueOf(42), execution.contextWrite(Context.of("tenant", "tenant-a")).block());
        assertEquals(2, calls.get());
        assertMetrics(monitor, 2, 0);
    }

    @Test
    public void deferredCallbacksPreserveDemandAndCancellation() {
        var monitor = new TaskExecutionMonitor();
        var source = TestPublisher.<Integer>createCold();
        StepVerifier.create(monitor.observe(TaskExecutionMonitor.defer(source, publisher -> monitor.observe(publisher.mono()))), 0)
            .expectSubscription()
            .then(() -> source.assertMaxRequested(0))
            .thenRequest(1)
            .then(() -> source.assertMinRequested(1))
            .thenCancel().verify();
        source.assertCancelled();
        assertMetrics(monitor, 1, 0);
    }

    @Test
    public void synchronousCallbackErrorsAndNullResultsAreObservedBeforeRecovery() {
        var monitor = new TaskExecutionMonitor();
        var failure = new IllegalStateException("callback");
        StepVerifier.create(monitor.observe(TaskExecutionMonitor.defer(42, input -> { throw failure; })))
            .expectErrorMatches(error -> error == failure).verify();
        StepVerifier.create(monitor.observe(TaskExecutionMonitor.defer(42, input -> null)))
            .expectError(NullPointerException.class).verify();
        assertMetrics(monitor, 2, 2);
    }

    @Test
    public void synchronousSubscriptionErrorsAreObservedBeforeRecoveryOnEverySubscription() {
        var monitor = new TaskExecutionMonitor();
        var failure = new IllegalStateException("subscription");
        Mono<Void> source = Mono.error(() -> { throw failure; });
        for (Mono<Void> execution : List.of(monitor.observe(source),
                monitor.observe(TaskExecutionMonitor.defer(42, input -> source)))) {
            StepVerifier.create(execution, 0)
                .expectSubscription()
                .expectErrorMatches(error -> error == failure).verify();
            StepVerifier.create(execution.onErrorComplete(), 0).verifyComplete();
            StepVerifier.create(monitor.observe(execution.onErrorComplete()), 0).verifyComplete();
        }
        assertMetrics(monitor, 6, 6);
    }

    @Test
    public void deferredExecutionsKeepDifferentMonitorBoundaries() {
        var first = new TaskExecutionMonitor();
        var second = new TaskExecutionMonitor();
        Mono<Void> inner = second.observe(TaskExecutionMonitor.defer(42,
            input -> Mono.<Void>error(new IllegalStateException("inner"))));
        first.observe(inner).onErrorComplete().block();
        assertMetrics(first, 1, 1);
        assertMetrics(second, 1, 1);
    }

    @Test
    public void parallelNestedFailuresShareOnlyOneFailureForTheOuterExecution() {
        var monitor = new TaskExecutionMonitor();
        Mono<Void> operation = Flux.range(0, 64)
            .flatMap(i -> monitor.observe(Mono.error(new IllegalStateException("nested")))
                .subscribeOn(Schedulers.parallel()).onErrorResume(error -> Mono.empty()), 16)
            .then();
        monitor.observe(operation).block();
        assertMetrics(monitor, 1, 1);
    }

    @Test
    public void concurrentIndependentExecutionsDoNotShareTheirFailureState() {
        var monitor = new TaskExecutionMonitor();
        Flux.range(0, 1000)
            .flatMap(i -> monitor.observe(Mono.defer(() -> i % 2 == 0
                    ? Mono.error(new IllegalStateException("failure")) : Mono.empty()))
                .onErrorResume(error -> Mono.empty()).subscribeOn(Schedulers.parallel()), 32)
            .blockLast();
        assertMetrics(monitor, 1000, 500);
    }

    @Test
    public void forwardsOriginalValueAndError() {
        var monitor = new TaskExecutionMonitor();
        Object value = new Object();
        StepVerifier.create(monitor.observe(Mono.just(value)))
            .assertNext(actual -> assertSame(value, actual)).verifyComplete();
        var failure = new IllegalStateException("original");
        StepVerifier.create(monitor.observe(Mono.error(failure)))
            .expectErrorMatches(error -> error == failure).verify();
        assertMetrics(monitor, 2, 1);
    }

    private static void assertMetrics(TaskExecutionMonitor monitor, long count, long errors) {
        var snapshot = monitor.snapshot();
        assertEquals(count, snapshot.getExecutionCount());
        assertEquals(errors, snapshot.getErrorCount());
        assertTrue(snapshot.getLastExecutionTime() > 0);
        assertTrue(Double.isFinite(snapshot.getMeanExecutionRate()));
        assertTrue(snapshot.getMeanExecutionRate() > 0);
        assertTrue(Double.isFinite(snapshot.getOneMinuteExecutionRate()));
        assertTrue(snapshot.getOneMinuteExecutionRate() >= 0);
    }
}
