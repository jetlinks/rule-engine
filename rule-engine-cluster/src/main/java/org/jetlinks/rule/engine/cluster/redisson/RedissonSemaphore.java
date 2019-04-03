package org.jetlinks.rule.engine.cluster.redisson;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.cluster.ClusterSemaphore;
import org.redisson.api.RSemaphore;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class RedissonSemaphore implements ClusterSemaphore {

    private RSemaphore rSemaphore;

    public RedissonSemaphore(RSemaphore rSemaphore) {
        this.rSemaphore = rSemaphore;
    }

    @Override
    @SneakyThrows
    public boolean tryAcquire(long timeout, TimeUnit timeUnit) {
        return rSemaphore.tryAcquire(timeout, timeUnit);
    }

    @Override
    public void release() {
        rSemaphore.release();
    }

    @Override
    public boolean delete() {
        return rSemaphore.delete();
    }

    @Override
    public CompletionStage<Boolean> tryAcquireAsync(long timeout, TimeUnit timeUnit) {
        return rSemaphore.tryAcquireAsync(timeout, timeUnit);
    }
}
