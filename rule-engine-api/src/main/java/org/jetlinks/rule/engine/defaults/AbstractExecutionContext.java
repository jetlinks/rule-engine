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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jetlinks.rule.engine.api.RuleData.HEADER_SOURCE_NODE_ID;

@Slf4j
public abstract class AbstractExecutionContext implements ExecutionContext {

    public static final String RECORD_DATA_TO_HEADER = RuleData.RECORD_DATA_TO_HEADER;

    public static final String RECORD_DATA_TO_HEADER_KEY = RuleData.RECORD_DATA_TO_HEADER_KEY;

    public static final String RECORD_DATA_TO_HEADER_KEY_PREFIX = RuleData.RECORD_DATA_TO_HEADER_KEY_PREFIX;

    @Getter
    private final Logger logger;

    @Setter
    @Getter
    private ScheduleJob job;

    @Getter
    private final EventBus eventBus;

    @Getter
    private Input input;

    @Getter
    private Output output;

    private Map<String, Output> eventOutputs;

    private final Function<ScheduleJob, Input> inputFactory;
    private final Function<ScheduleJob, Output> outputFactory;
    private final Function<ScheduleJob, Map<String, Output>> eventOutputsFactory;

    private volatile List<Runnable> shutdownListener;

    private final GlobalScope globalScope;

    //记录数据到RuleData的header中,方便透传到下游数据
    private boolean recordDataToHeader;

    //记录数据到RuleData的header中的key
    private String recordDataToHeaderKey;

    @Setter
    @Getter
    private boolean debug;

    public AbstractExecutionContext(String workerId,
                                    ScheduleJob job,
                                    EventBus eventBus,
                                    Logger logger,
                                    Function<ScheduleJob, Input> inputFactory,
                                    Function<ScheduleJob, Output> outputFactory,
                                    Function<ScheduleJob, Map<String, Output>> eventOutputsFactory,
                                    GlobalScope globalScope) {

        this.job = job;
        this.eventBus = eventBus;
        this.inputFactory = inputFactory;
        this.outputFactory = outputFactory;
        this.eventOutputsFactory = eventOutputsFactory;
        this.logger = CompositeLogger.of(logger, new EventLogger(eventBus, job.getInstanceId(), job.getNodeId(), workerId));
        this.globalScope = globalScope;
        init();
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
                .write(data)
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

        if (recordDataToHeader) {
            ruleData.setHeader(recordDataToHeaderKey, ruleData.getData());
        }
        ruleData.setHeader(HEADER_SOURCE_NODE_ID, getJob().getNodeId());
        return ruleData;
    }

    @Override
    public RuleData newRuleData(RuleData source, Object data) {
        RuleData ruleData = source.newData(data);

        if (recordDataToHeader) {
            ruleData.setHeader(recordDataToHeaderKey, ruleData.getData());
        }
        ruleData.setHeader(HEADER_SOURCE_NODE_ID, getJob().getNodeId());
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
        List<Runnable> shutdownListener = this.shutdownListener;
        if (shutdownListener == null) {
            return;
        }
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
        if (shutdownListener == null) {
            synchronized (this) {
                if (shutdownListener == null) {
                    shutdownListener = new CopyOnWriteArrayList<>();
                }
            }
        }
        shutdownListener.add(runnable);
    }

    @Override
    public GlobalScope global() {
        return globalScope;
    }

    private void init() {
        this.input = RuleEngineHooks.wrapInput(inputFactory.apply(job));
        this.output = RuleEngineHooks.wrapOutput(outputFactory.apply(job));
        this.eventOutputs = eventOutputsFactory
            .apply(job)
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> RuleEngineHooks.wrapOutput(e.getValue())));

        if (this.eventOutputs.isEmpty()) {
            this.eventOutputs = Collections.emptyMap();
        }
        recordDataToHeader = job
            .getConfiguration(AbstractExecutionContext.RECORD_DATA_TO_HEADER)
            .map(v -> "true".equals(String.valueOf(v)))
            .orElse(Boolean.getBoolean("rule.engine.record_data_to_header"));

        if (recordDataToHeader) {
            recordDataToHeaderKey =
                RECORD_DATA_TO_HEADER_KEY_PREFIX + job
                    .getConfiguration(AbstractExecutionContext.RECORD_DATA_TO_HEADER_KEY)
                    .map(String::valueOf)
                    .orElse(job.getNodeId());
        } else {
            recordDataToHeaderKey = null;
        }

    }

    public void reload() {
        init();
    }
}
