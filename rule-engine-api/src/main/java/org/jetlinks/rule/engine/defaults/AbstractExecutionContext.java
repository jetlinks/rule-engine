package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.utils.StringUtils;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scope.GlobalScope;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.Input;
import org.jetlinks.rule.engine.api.task.Output;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractExecutionContext implements ExecutionContext {

    @Getter
    private final Logger logger;

    @Setter
    @Getter
    private ScheduleJob job;

    @Getter
    private final EventBus eventBus;

    @Getter
    private final Input input;

    @Getter
    private final Output output;

    private final Map<String, Output> eventOutputs;

    private final List<Runnable> shutdownListener = new CopyOnWriteArrayList<>();

    private final GlobalScope globalScope;
    @Setter
    @Getter
    private boolean debug;

    public AbstractExecutionContext(String workerId,
                                    ScheduleJob job,
                                    EventBus eventBus,
                                    Logger logger,
                                    Input input,
                                    Output output,
                                    Map<String, Output> eventOutputs,
                                    GlobalScope globalScope) {

        this.job = job;
        this.eventBus = eventBus;
        this.input = RuleEngineHooks.wrapInput(input);
        this.output = RuleEngineHooks.wrapOutput(output);
        this.eventOutputs = eventOutputs
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> RuleEngineHooks.wrapOutput(e.getValue())));
        this.logger = CompositeLogger.of(logger, new EventLogger(eventBus, job.getInstanceId(), job.getNodeId(), workerId));
        this.globalScope = globalScope;
    }

    @Override
    public String getInstanceId() {
        return job.getInstanceId();
    }

    @Override
    public <T> Mono<T> fireEvent(@Nonnull String event, @Nonnull RuleData data) {
        //规则自定义配置
        data.setHeader(RuleConstants.Headers.ruleConfiguration, getJob().getRuleConfiguration());
        //任务执行器标识
        data.setHeader(RuleConstants.Headers.jobExecutor, getJob().getExecutor());
        //模型类型
        data.setHeader(RuleConstants.Headers.modelType, getJob().getModelType());

        Mono<T> then = eventBus
                .publish(RuleConstants.Topics.event(job.getInstanceId(), job.getNodeId(), event), data)
                .doOnSubscribe(ignore -> log.trace("fire job task [{}] event [{}] ", job, event))
                .then(Mono.empty());
        Output output = eventOutputs.get(event);
        if (output != null) {
            return output
                    .write(Mono.just(data))
                    .then(then);
        }
        return then;
    }

    @Override
    public <T> Mono<T> onError(@Nullable Throwable e, @Nullable RuleData sourceData) {
        return fireEvent(RuleConstants.Event.error, createErrorData(e, sourceData));
    }

    private RuleData createErrorData(Throwable e, RuleData source) {
        Map<String, Object> error = new HashMap<>();
          /*
          {
             "error":{
                "message":"",
                "source": {
                    "id":"",
                    "type":"",
                    "name":""
                }
             }
          }
        */
        if (e != null) {
            error.put("type", e.getClass().getSimpleName());
            error.put("message", e.getMessage());
            error.put("stack", StringUtils.throwable2String(e));
        }
        Map<String, Object> sourceInfo = new HashMap<>();
        sourceInfo.put("id", getJob().getNodeId());
        sourceInfo.put("type", getJob().getExecutor());
        sourceInfo.put("name", getJob().getName());
        error.put("source", sourceInfo);

        Map<String, Object> value = new HashMap<>();
        if (source != null) {
            source.acceptMap(value::putAll);
        }
        value.put(value.containsKey("error") ? "_error" : "error", error);
        return newRuleData(source == null ? value : source.newData(value));
    }

    @Override
    public RuleData newRuleData(Object data) {
        RuleData ruleData = RuleData.create(data);

        ruleData.setHeader("sourceNode", getJob().getNodeId());
        return ruleData;
    }

    @Override
    public Mono<Void> shutdown(String code, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        data.put("message", message);
        return eventBus
                .publish(RuleConstants.Topics.shutdown(job.getInstanceId(), job.getNodeId()), data)
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

    @Override
    public GlobalScope global() {
        return globalScope;
    }
}
