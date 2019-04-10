package org.jetlinks.rule.engine.cluster.redisson;

import org.jetlinks.rule.engine.api.cluster.ClusterLock;
import org.redisson.api.RSemaphore;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class RedissonLock implements ClusterLock {

    private RSemaphore semaphore;

    public RedissonLock(RSemaphore semaphore) {
        this.semaphore = semaphore;
    }

    @Override
    public void unlock() {
        semaphore.release();
    }
}
