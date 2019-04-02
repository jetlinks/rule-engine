package org.jetlinks.rule.engine.cluster;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public interface ClusterSemaphore {

    boolean tryAcquire(long timeout, TimeUnit timeUnit);

    void release();

    CompletionStage<Boolean> tryAcquireAsync(long timeout, TimeUnit timeUnit);

}
