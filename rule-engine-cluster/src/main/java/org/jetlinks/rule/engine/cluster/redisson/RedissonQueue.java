package org.jetlinks.rule.engine.cluster.redisson;

import io.reactivex.disposables.Disposable;
import lombok.SneakyThrows;
import org.jetlinks.rule.engine.cluster.Queue;
import org.redisson.api.RBlockingQueueAsync;
import org.redisson.api.RBlockingQueueReactive;
import org.redisson.api.RQueue;
import org.redisson.rx.RedissonBlockingQueueRx;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class RedissonQueue<T> implements Queue<T> {

    private RBlockingQueueAsync<T> queue;

    private RedissonBlockingQueueRx<T> queueRx;

    private volatile AtomicReference<Consumer<T>> consumer = new AtomicReference<>();

    private Disposable disposable;

    private volatile boolean accepted;

    public RedissonQueue(RBlockingQueueAsync<T> queue) {
        this.queueRx = new RedissonBlockingQueueRx<>(queue);
        this.queue = queue;
    }

    public void start() {
        accepted = true;
        disposable = queueRx
                .takeElements()
                .subscribe(data -> {
                    if (this.consumer.get() != null) {
                        this.consumer.get().accept(data);
                    }
                });
    }

    @Override
    public void accept(Consumer<T> consumer) {
        if (this.consumer.get() == null) {
            this.consumer.set(consumer);
        } else {
            this.consumer.set(this.consumer.get().andThen(consumer));
        }
        if (!accepted) {
            start();
        }
    }

    @Override
    public boolean acceptOnce(Consumer<T> consumer) {
        this.consumer.set(consumer);
        if (!accepted) {
            start();
        }
        return true;
    }

    @Override
    public CompletionStage<Boolean> putAsync(T data) {
        return queue.addAsync(data);
    }

    @Override
    @SneakyThrows
    public void put(T data) {
        queue.addAsync(data)
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        disposable.dispose();
        consumer.set(null);
    }
}
