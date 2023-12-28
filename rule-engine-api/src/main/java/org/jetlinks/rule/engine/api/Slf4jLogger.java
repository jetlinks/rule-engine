package org.jetlinks.rule.engine.api;

import lombok.extern.slf4j.Slf4j;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Slf4j
public class Slf4jLogger implements Logger {

    private final String name;

    public Slf4jLogger(String name) {
        this.name = name;
    }

    @Override
    public void trace(String text, Object... args) {
        log.trace(text, args);
    }

    @Override
    public void info(String message, Object... args) {
        if (log.isInfoEnabled()) {
            log.info(name + ":" + message, args);
        }
    }

    @Override
    public void debug(String message, Object... args) {
        if (log.isDebugEnabled()) {
            log.debug(name + ":" + message, args);
        }
    }

    @Override
    public void warn(String message, Object... args) {
        log.warn(name + ":" + message, args);
    }

    @Override
    public void error(String message, Object... args) {
        log.error(name + ":" + message, args);
    }
}
