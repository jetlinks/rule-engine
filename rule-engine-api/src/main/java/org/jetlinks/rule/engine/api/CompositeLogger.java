package org.jetlinks.rule.engine.api;

import lombok.AllArgsConstructor;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.List;

@AllArgsConstructor
public class CompositeLogger implements Logger {

    private final List<Logger> loggers;

    public static Logger of(Logger... loggers) {
        return new CompositeLogger(Arrays.asList(loggers));
    }

    @Override
    public void info(String message, Object... args) {
        for (Logger logger : loggers) {
            logger.info(message, args);
        }
    }

    @Override
    public void debug(String message, Object... args) {
        for (Logger logger : loggers) {
            logger.debug(message, args);
        }
    }

    @Override
    public void warn(String message, Object... args) {
        for (Logger logger : loggers) {
            logger.warn(message, args);
        }
    }

    @Override
    public void error(String message, Object... args) {
        for (Logger logger : loggers) {
            logger.error(message, args);
        }
    }

    @Override
    public void log(Level level, String message, Object... args) {
        for (Logger logger : loggers) {
            logger.log(level, message, args);
        }
    }
}
