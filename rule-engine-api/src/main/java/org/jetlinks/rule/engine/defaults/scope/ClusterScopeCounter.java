package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.core.cluster.ClusterCounter;
import org.jetlinks.rule.engine.api.scope.ScropeCounter;
import reactor.core.publisher.Mono;

public class ClusterScopeCounter implements ScropeCounter {

    ClusterCounter counter;

    @Override
    public Mono<Double> inc(double n) {
        return counter.increment(n);
    }

    @Override
    public Mono<Double> dec(double n) {
        return counter.decrement(n);
    }

    @Override
    public Mono<Double> get() {
        return counter.get();
    }

    @Override
    public Mono<Double> set(double value) {
        return counter.set(value);
    }

    @Override
    public Mono<Double> setAndGet(double value) {
        return counter.setAndGet(value);
    }

    @Override
    public Mono<Double> getAndSet(double value) {
        return counter.getAndSet(value);
    }
}
