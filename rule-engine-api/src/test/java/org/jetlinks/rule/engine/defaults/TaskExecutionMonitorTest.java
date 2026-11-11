package org.jetlinks.rule.engine.defaults;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;
import reactor.util.context.Context;

import static org.junit.Assert.*;

public class TaskExecutionMonitorTest {
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
        monitor.observe(nested.onErrorResume(error -> Mono.empty())).block();
        assertMetrics(monitor, 1, 1);
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
    }
}
