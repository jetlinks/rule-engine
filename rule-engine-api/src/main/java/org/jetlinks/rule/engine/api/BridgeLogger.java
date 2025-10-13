package org.jetlinks.rule.engine.api;

import org.jetlinks.core.monitor.logger.Logger;
import org.slf4j.event.Level;

public class BridgeLogger implements Logger {

    private final org.jetlinks.rule.engine.api.Logger logger;

    public BridgeLogger(org.jetlinks.rule.engine.api.Logger logger) {
        this.logger = logger;
    }

    @Override
    public void log(Level level, String message, Object... args) {
        switch (level) {
            case TRACE:
                logger.trace(message, args);
                return;
            case DEBUG:
                logger.debug(message, args);
                return;
            case INFO:
                logger.info(message, args);
                return;
            case WARN:
                logger.warn(message, args);
                return;
            case ERROR:
                logger.error(message, args);
        }
    }
}
