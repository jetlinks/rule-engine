package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.utils.StringUtils;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.slf4j.helpers.MessageFormatter;

import java.util.Arrays;
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
        eventBus.publish(RuleConstants.Topics.logger(instanceId, nodeId, level), createLog(level, message, args))
                .subscribe();
    }

    private LogEvent createLog(String level, String message, Object... args) {

        String exception = Arrays.stream(args)
                .filter(Throwable.class::isInstance)
                .map(Throwable.class::cast)
                .map(StringUtils::throwable2String)
                .collect(Collectors.joining());

        return LogEvent.builder()
                .level(level)
                .message(MessageFormatter.arrayFormat(message, args).getMessage())
                .instanceId(instanceId)
                .nodeId(nodeId)
                .workerId(workerId)
                .timestamp(System.currentTimeMillis())
                .exception(exception)
                .build();
    }
}
