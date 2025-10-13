package org.jetlinks.rule.engine.api;

import org.slf4j.event.Level;

class CompatibleLogger implements Logger{
    private final org.jetlinks.core.monitor.logger.Logger logger;

    CompatibleLogger(org.jetlinks.core.monitor.logger.Logger logger) {
        this.logger = logger;
    }

    @Override
    public void trace(String text, Object... args) {
        logger.trace(text,args);
    }

    @Override
    public void info(String message, Object... args) {
        logger.info(message,args);
    }

    @Override
    public void debug(String message, Object... args) {
        logger.debug(message,args);
    }

    @Override
    public void warn(String message, Object... args) {
        logger.warn(message,args);
    }

    @Override
    public void error(String message, Object... args) {
        logger.error(message,args);
    }

    @Override
    public String getName() {
        return logger.getName();
    }

    @Override
    public void log(Level level, String message, Object... args) {
        logger.log(level, message, args);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public boolean isEnabled(Level level) {
        return logger.isEnabled(level);
    }

    @Override
    public org.slf4j.Logger slf4j() {
        return logger.slf4j();
    }
}
