package org.jetlinks.rule.engine.singleton;

import org.jetlinks.rule.engine.api.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class Slf4jLogger implements Logger {

    private org.slf4j.Logger logger;

    public Slf4jLogger(String name) {
        logger = LoggerFactory.getLogger(name);
    }

    @Override
    public void info(String message, Object... args) {
        logger.info(message, args);
    }

    @Override
    public void debug(String message, Object... args) {
        logger.debug(message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        logger.warn(message, args);
    }

    @Override
    public void error(String message, Object... args) {
        logger.error(message, args);
    }
}
