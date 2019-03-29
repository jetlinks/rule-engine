package org.jetlinks.rule.engine.api;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface Logger {
    void info(String message, Object... args);

    void debug(String message, Object... args);

    void warn(String message, Object... args);

    void error(String message, Object... args);
}
