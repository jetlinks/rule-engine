package org.jetlinks.rule.engine.api.cluster;

import java.util.concurrent.CompletionStage;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ClusterObject<T> {
    T getData();

    void setData(T data);

    CompletionStage<T> getAndDeleteAsync();

    void delete();
}
