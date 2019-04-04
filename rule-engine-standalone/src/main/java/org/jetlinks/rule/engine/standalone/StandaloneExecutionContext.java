package org.jetlinks.rule.engine.standalone;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class StandaloneExecutionContext implements ExecutionContext {
    private Logger logger;

    private Object data;

    @Getter
    @Setter
    private Map<String, Object> attributes = new HashMap<>();

    public StandaloneExecutionContext(Logger logger, Object data) {
        this.logger = logger;
        this.data = data;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public Optional<Object> getAttribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    @Override
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}
