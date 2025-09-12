package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.i18n.LocaleUtils;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.utils.ExceptionUtils;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.slf4j.helpers.MessageFormatter;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
@Setter
@AllArgsConstructor
public class EventLogger implements Logger {

    private final EventBus eventBus;

    private String instanceId;

    private String nodeId;

    private String workerId;

    @Override
    public void trace(String message, Object... args) {
        publishLog("trace", message, args);
    }

    @Override
    public void info(String message, Object... args) {
        publishLog("info", message, args);
    }

    @Override
    public void debug(String message, Object... args) {
        publishLog("debug", message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        publishLog("warn", message, args);
    }

    @Override
    public void error(String message, Object... args) {
        publishLog("error", message, args);
    }

    private void publishLog(String level, String message, Object... args) {
        eventBus
            .publish(RuleConstants.Topics.logger0(instanceId, nodeId, level),
                     () -> createLog(level, message, args))
            .subscribe();
    }

    private LogEvent createLog(String level, String message, Object... args) {
        Throwable error = MessageFormatter.getThrowableCandidate(args);

        if (null != error) {
            args = MessageFormatter.trimmedCopy(args);
        }
        String msg;
        String i18nMaybe = LocaleUtils.resolveMessage(message, args);

        // 匹配了国际化
        if (!Objects.equals(message, i18nMaybe)) {
            msg = i18nMaybe;
        } else {
            msg = MessageFormatter.arrayFormat(message, args).getMessage();
        }

        String exception = ExceptionUtils.getStackTrace(error);

        return LogEvent
            .builder()
            .level(level)
            .message(msg)
            .instanceId(instanceId)
            .nodeId(nodeId)
            .workerId(workerId)
            .timestamp(System.currentTimeMillis())
            .exception(exception)
            .build();
    }
}
