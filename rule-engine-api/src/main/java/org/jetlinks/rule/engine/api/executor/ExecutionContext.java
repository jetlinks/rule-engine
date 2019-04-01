package org.jetlinks.rule.engine.api.executor;

import org.jetlinks.rule.engine.api.Logger;

import java.util.Map;
import java.util.Optional;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ExecutionContext {
    Logger logger();

    Object getData();

    Map<String, Object> getAttributes();

    Optional<Object> getAttribute(String key);

    void setAttribute(String key, Object value);

}
