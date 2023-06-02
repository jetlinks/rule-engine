package org.jetlinks.rule.engine.defaults.scope;

import org.jetlinks.rule.engine.api.scope.ScopeCounter;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

class InMemoryScopeCounter implements ScopeCounter {

    AtomicReference<Double> ref = new AtomicReference<>(0D);

    @Override
    public Mono<Double> inc(double n) {
        return Mono.just(ref.updateAndGet(v -> v + n));
    }

    @Override
    public Mono<Double> dec(double n) {
        return Mono.just(ref.updateAndGet(v -> v - n));
    }

    @Override
    public Mono<Double> get() {
        return Mono.just(ref.get());
    }

    @Override
    public Mono<Double> set(double value) {
        return getAndSet(value);
    }

    @Override
    public Mono<Double> setAndGet(double value) {
        return Mono.just(ref.updateAndGet(v -> value));
    }

    @Override
    public Mono<Double> getAndSet(double value) {
        return Mono.just(ref.getAndSet(value));
    }

    @Override
    public Mono<Double> remove() {
        return Mono.justOrEmpty(ref.getAndSet(0D));
    }
}
