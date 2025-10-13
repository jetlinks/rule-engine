package org.jetlinks.rule.engine.api;

import org.slf4j.event.Level;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface Logger extends org.jetlinks.core.monitor.logger.Logger {

    default void trace(String text, Object... args) {
    }

    void info(String message, Object... args);

    void debug(String message, Object... args);

    void warn(String message, Object... args);

    void error(String message, Object... args);

    @Override
    default void log(Level level, String message, Object... args) {
        switch (level) {
            case DEBUG -> debug(message, args);
            case INFO -> info(message, args);
            case WARN -> warn(message, args);
            case ERROR -> error(message, args);
            case TRACE -> trace(message, args);
        }
    }

    static Logger of(org.jetlinks.core.monitor.logger.Logger logger){
        if(logger instanceof Logger){
            return (Logger)logger;
        }
        return new CompatibleLogger(logger);
    }
}
