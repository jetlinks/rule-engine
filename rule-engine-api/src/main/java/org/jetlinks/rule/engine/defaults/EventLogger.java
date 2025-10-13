package org.jetlinks.rule.engine.defaults;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.i18n.LocaleUtils;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.lang.SeparatedCharSequence;
import org.jetlinks.core.lang.SeparatedString;
import org.jetlinks.core.utils.ExceptionUtils;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
public class EventLogger implements Logger {

    public static final SeparatedCharSequence LOG_TEMPLATE =
        SeparatedString.create('.', "rule", "engine", "*", "*");

    private final EventBus eventBus;

    private String instanceId;

    private String nodeId;

    private String workerId;

    @Override
    public String getName() {
        return LOG_TEMPLATE.replace(2, instanceId, 3, nodeId).toString();
    }

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

    @Override
    public void log(Level level, String message, Object... args) {
        publishLog(level.name().toLowerCase(), message, args);
    }

    private void publishLog(String level, String message, Object... args) {
        eventBus
            .publish(RuleConstants.Topics.logger0(instanceId, nodeId, level),
                     Mono.deferContextual(ctx -> Mono.just(createLog(ctx, level, message, args))))
            .subscribe();
    }

    private LogEvent createLog(ContextView ctx, String level, String message, Object... args) {
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

        LogEvent event = LogEvent
            .builder()
            .level(level)
            .message(msg)
            .instanceId(instanceId)
            .nodeId(nodeId)
            .workerId(workerId)
            .timestamp(System.currentTimeMillis())
            .exception(exception)
            .build();

        Context traceContext = ctx
            .<Context>getOrEmpty(Context.class)
            .orElseGet(Context::current);
        SpanContext spanContext = Span.fromContext(traceContext).getSpanContext();
        if (spanContext.isValid()) {
            event.setTraceId(spanContext.getTraceId());
            event.setSpanId(spanContext.getSpanId());
        }
        return event;
    }
}
