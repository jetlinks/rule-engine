package org.jetlinks.rule.engine.cluster.redisson;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.cluster.ClusterObject;
import org.redisson.api.RBucket;

import java.util.concurrent.CompletionStage;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@AllArgsConstructor
public class RedissonClusterObject<T> implements ClusterObject<T> {
    RBucket<T> rBucket;

    @Override
    public T getData() {
        return rBucket.get();
    }

    @Override
    public void setData(T data) {
        rBucket.set(data);
    }

    @Override
    public CompletionStage<T> getAndDeleteAsync() {
        return rBucket.getAndDeleteAsync();
    }

    @Override
    public void delete() {
        rBucket.delete();
    }
}
