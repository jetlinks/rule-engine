package org.jetlinks.rule.engine.api;

import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.i18n.LocaleUtils;
import org.slf4j.event.Level;

import java.util.Objects;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Slf4j
public class Slf4jLogger implements Logger {

    private final CharSequence name;

    public Slf4jLogger(CharSequence name) {
        this.name = name;
    }

    public static boolean isEnabled() {
        return log.isErrorEnabled();
    }

    @Override
    public boolean isEnabled(Level level) {
        return log.isEnabledForLevel(level);
    }

    @Override
    public void trace(String text, Object... args) {
        log.trace(text, args);
    }

    @Override
    public void info(String message, Object... args) {
        if (log.isInfoEnabled()) {
            String msg = LocaleUtils.resolveMessage(message, message, args);
            if (Objects.equals(msg, message)) {
                log.info(name + ":" + message, args);
            } else {
                log.info("{}:{}", name, message);
            }
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
        if (log.isWarnEnabled()) {
            String msg = LocaleUtils.resolveMessage(message, message, args);
            if (Objects.equals(msg, message)) {
                log.warn(name + ":" + message, args);
            } else {
                log.warn("{}:{}", name, message);
            }
        }
    }

    @Override
    public void error(String message, Object... args) {
        log.error(name + ":" + message, args);
    }
}
