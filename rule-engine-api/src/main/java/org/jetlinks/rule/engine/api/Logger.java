package org.jetlinks.rule.engine.api;


/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface Logger {

    default void trace(String text, Object... args) {
    }

    void info(String message, Object... args);

    void debug(String message, Object... args);

    void warn(String message, Object... args);

    void error(String message, Object... args);

    default org.jetlinks.core.monitor.logger.Logger toMonitorLogger() {
        return new BridgeLogger(this);
    }
}
