package org.jetlinks.rule.engine.cluster.redisson;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.cluster.Queue;
import org.redisson.api.RQueue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class RedissonQueue<T> implements Queue<T> {

    private RQueue<T> queue;

    private volatile AtomicReference<Consumer<T>> consumer = new AtomicReference<>();

    private volatile boolean accepted;

    public RedissonQueue(RQueue<T> queue) {
        this.queue = queue;
    }

    public void start() {
        accepted = true;
        flush();
    }

    private final AtomicInteger flushThread = new AtomicInteger(0);

    public void flush() {
        if (accepted && flushThread.get() < 5) {
            try {
                flushThread.incrementAndGet();
                for (T data = queue.poll(); data != null; data = queue.poll()) {
                    Consumer<T> consumer;
                    if ((consumer = this.consumer.get()) != null) {
                        consumer.accept(data);
                    } else {
                        queue.add(data);
                        break;
                    }
                }
            } finally {
                flushThread.decrementAndGet();
            }
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
        return CompletableFuture.supplyAsync(() -> queue.add(data));
    }

    @Override
    @SneakyThrows
    public void put(T data) {
        if (!queue.add(data)) {
            throw new RuntimeException("add data to queue fail: " + data);
        }
    }

    @Override
    public void stop() {
        consumer.set(null);
    }
}
