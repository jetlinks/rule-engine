package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.utils.StringUtils;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.events.EventBus;
import org.jetlinks.rule.engine.api.executor.Input;
import org.jetlinks.rule.engine.api.executor.Output;
import org.jetlinks.rule.engine.api.executor.ScheduleJob;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Slf4j
public class DefaultExecutionContext implements ExecutionContext {

    private final Logger logger;

    @Setter
    private ScheduleJob job;

    @Getter
    private final EventBus eventBus;

    private final Input input;

    private final Output output;

    private final List<Runnable> shutdownListener = new CopyOnWriteArrayList<>();

    @Setter
    private boolean debug;

    public DefaultExecutionContext(ScheduleJob job, EventBus eventBus, ConditionEvaluator evaluator) {
        this.job = job;
        this.eventBus = eventBus;
        this.logger = new Slf4jLogger("rule.engine." + job.getInstanceId() + "." + job.getNodeId());
        this.input = new EventBusInput(job.getInstanceId(), job.getNodeId(), job.getEvents(), eventBus);

        this.output = new EventBusOutput(job.getInstanceId(), eventBus, job.getOutputs(), evaluator);
    }

    @Override
    public String getInstanceId() {
        return job.getInstanceId();
    }

    @Override
    public Mono<Void> fireEvent(@Nonnull String event, @Nonnull RuleData data) {

        return eventBus
                .publish(RuleConstants.Topics.event(job.getInstanceId(), job.getNodeId(), event), Mono.just(data))
                .doOnSubscribe(ignore -> logger.debug("fire job task [{}] event [{}] ", job, event))
                .then();
    }

    @Override
    public Mono<Void> onError(@Nullable Throwable e, @Nullable RuleData data) {
        return fireEvent(RuleConstants.Event.error, createErrorData(e, data));
    }

    private RuleData createErrorData(Throwable e, RuleData source) {
        Map<String, Object> obj = new HashMap<>();
        if (e != null) {
            obj.put("type", e.getClass().getSimpleName());
            obj.put("message", e.getMessage());
            obj.put("stack", StringUtils.throwable2String(e));
        }
        obj.put("source", source);
        return source == null ? RuleData.create(obj) : source.newData(obj);
    }

    @Override
    public Mono<Void> shutdown(String code, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("message", message);
        return eventBus
                .publish(RuleConstants.Topics.shutdown(job.getInstanceId(), job.getNodeId()), Mono.just(data))
                .then();
    }

    public void doShutdown() {
        for (Runnable runnable : shutdownListener) {
            try {
                runnable.run();
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }

    @Override
    public void onShutdown(Runnable runnable) {
        shutdownListener.add(runnable);
    }

}
