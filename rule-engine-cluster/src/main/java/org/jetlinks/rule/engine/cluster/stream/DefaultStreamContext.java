package org.jetlinks.rule.engine.cluster.stream;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.StreamExecutionContext;
import org.jetlinks.rule.engine.api.stream.Input;
import org.jetlinks.rule.engine.api.stream.Output;
import org.jetlinks.rule.engine.cluster.ClusterMap;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public class DefaultStreamContext implements StreamExecutionContext {

    @Getter
    @Setter
    private Input input;

    @Getter
    @Setter
    private Output output;

    @Getter
    @Setter
    private Logger logger;

    @Setter
    private ClusterMap<String, Object> attributes;

    @Getter
    @Setter
    private BiConsumer<RuleData, Throwable> errorHandler;


    @Override
    public void onError(RuleData data, Throwable e) {
        if (null != errorHandler) {
            errorHandler.accept(data, e);
        } else {
            logger.error("unhandled error", e);
        }
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Object getData() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes.toMap();
    }

    @Override
    public Optional<Object> getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}
