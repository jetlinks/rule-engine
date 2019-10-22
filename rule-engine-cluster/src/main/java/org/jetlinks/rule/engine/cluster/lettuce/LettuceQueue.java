package org.jetlinks.rule.engine.cluster.lettuce;

import org.jetlinks.lettuce.RedisQueue;
import org.jetlinks.rule.engine.api.cluster.Queue;
import org.reactivestreams.Publisher;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public class LettuceQueue<T> implements Queue<T> {

    private RedisQueue<T> redisQueue;

    private FluxProcessor<T,T> processor = EmitterProcessor.create(true);


    public LettuceQueue(RedisQueue<T> redisQueue) {
        this.redisQueue = redisQueue;
    }

    @Override
    public Flux<T> poll() {
        return processor.map(Function.identity());
    }

    @Override
    public Mono<Boolean> put(Publisher<T> data) {
        return Flux.from(data)
                .flatMap(d->Mono.fromCompletionStage(redisQueue.addAsync(d)))
                .all(t->t);
    }

    @Override
    public Mono<Boolean> start() {
        redisQueue.poll(processor::onNext);

        return Mono.just(true);
    }

    @Override
    public Mono<Boolean> stop() {
        processor.onComplete();
        return Mono.just(true);
    }

    @Override
    public void setLocalConsumerPoint(float point) {
        redisQueue.setLocalConsumerPoint(point);
    }
}
