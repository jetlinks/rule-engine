package org.jetlinks.rule.engine.cluster.lettuce;

import lombok.SneakyThrows;
import org.jetlinks.lettuce.RedisQueue;
import org.jetlinks.rule.engine.api.cluster.Queue;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class LettuceQueue<T> implements Queue<T> {

    private RedisQueue<T> redisQueue;

    volatile Consumer<T> listener;

    public LettuceQueue(RedisQueue<T> redisQueue) {
        this.redisQueue = redisQueue;
    }

    @Override
    public boolean acceptOnce(Consumer<T> consumer) {
        if (listener != null) {
            redisQueue.removeListener(listener);
        }
        listener = consumer;
        redisQueue.poll(listener);
        return true;
    }

    @Override
    public CompletionStage<Boolean> putAsync(T data) {

        return redisQueue.addAsync(data);
    }

    @Override
    @SneakyThrows
    public void put(T data) {
        redisQueue.addAsync(data);
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }
}
