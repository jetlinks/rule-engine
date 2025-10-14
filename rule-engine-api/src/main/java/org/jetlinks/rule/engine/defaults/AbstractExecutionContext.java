package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.Lazy;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.metrics.Metrics;
import org.jetlinks.core.monitor.recorder.Recorder;
import org.jetlinks.core.monitor.tracer.Tracer;
import org.jetlinks.core.utils.ExceptionUtils;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.jetlinks.rule.engine.api.RuleData.HEADER_SOURCE_NODE_ID;

@Slf4j
public abstract class AbstractExecutionContext implements ExecutionContext, Monitor {

    public static final String RECORD_DATA_TO_HEADER = RuleData.RECORD_DATA_TO_HEADER;

    public static final String RECORD_DATA_TO_HEADER_KEY = RuleData.RECORD_DATA_TO_HEADER_KEY;

    public static final String RECORD_DATA_TO_HEADER_KEY_PREFIX = RuleData.RECORD_DATA_TO_HEADER_KEY_PREFIX;


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

    private final Function<ScheduleJob,Monitor> monitorFactory;
    private final Function<ScheduleJob, Input> inputFactory;
    private final Function<ScheduleJob, Output> outputFactory;
    private final Function<ScheduleJob, Map<String, Output>> eventOutputsFactory;

    private volatile List<Runnable> shutdownListener;

    private final Supplier<GlobalScope> scopeSupplier;

    //记录数据到RuleData的header中,方便透传到下游数据
    private boolean recordDataToHeader;

    //记录数据到RuleData的header中的key
    private String recordDataToHeaderKey;

    @Setter
    @Getter
    private boolean debug;

    private volatile GlobalScope loadedScope;

    @Getter
    private Logger logger;

    private Monitor monitor;

    public AbstractExecutionContext(ScheduleJob job,
                                    EventBus eventBus,
                                    Function<ScheduleJob, Monitor> monitorFactory,
                                    Function<ScheduleJob, Input> inputFactory,
                                    Function<ScheduleJob, Output> outputFactory,
                                    Function<ScheduleJob, Map<String, Output>> eventOutputsFactory,
                                    Supplier<GlobalScope> scopeSupplier) {

        this.job = job;
        this.eventBus = eventBus;
        this.inputFactory = inputFactory;
        this.outputFactory = outputFactory;
        this.eventOutputsFactory = eventOutputsFactory;
        this.monitorFactory= monitorFactory;
        this.scopeSupplier = scopeSupplier;
        init();
    }

    public AbstractExecutionContext(String workerId,
                                    ScheduleJob job,
                                    EventBus eventBus,
                                    Logger logger,
                                    Function<ScheduleJob, Input> inputFactory,
                                    Function<ScheduleJob, Output> outputFactory,
                                    Function<ScheduleJob, Map<String, Output>> eventOutputsFactory,
                                    Supplier<GlobalScope> scopeSupplier) {

        this.job = job;
        this.eventBus = eventBus;
        this.inputFactory = inputFactory;
        this.outputFactory = outputFactory;
        this.eventOutputsFactory = eventOutputsFactory;
        this.logger = logger == null
            ? new EventLogger(eventBus, job.getInstanceId(), job.getNodeId(), workerId)
            : CompositeLogger.of(logger, new EventLogger(eventBus, job.getInstanceId(), job.getNodeId(), workerId));
        this.scopeSupplier = scopeSupplier;
        this.monitor = Monitor.noop();
        this.monitorFactory= ignore->Monitor.noop();
        init();
    }

    @Override
    public String getInstanceId() {
        return job.getInstanceId();
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Tracer tracer() {
        return monitor.tracer();
    }

    @Override
    public Metrics metrics() {
        return monitor.metrics();
    }

    @Override
    public Recorder recorder() {
        return monitor.recorder();
    }

    @Override
    public Monitor monitor() {
        return this;
    }

    @Override
    public <T> Mono<T> fireEvent(@Nonnull String event,
                                 @Nonnull Supplier<RuleData> supplier) {

        Supplier<RuleData> builder =
            Lazy.of(() -> {
                RuleData data = supplier.get();
                data.setHeader(RuleConstants.Headers.ruleConfiguration, getJob().getRuleConfiguration());
                data.setHeader(RuleConstants.Headers.jobExecutor, getJob().getExecutor());
                data.setHeader(RuleConstants.Headers.modelType, getJob().getModelType());
                return data;
            });

        Mono<T> then = eventBus
            .publish(RuleConstants.Topics.event0(job.getInstanceId(), job.getNodeId(), event), builder)
            .then(Mono.empty());

        Output output = eventOutputs.get(event);

        if (output != null) {
            return output
                .write(builder.get())
                .then(then);
        }
        return then;
    }

    @Override
    public <T> Mono<T> fireEvent(@Nonnull String event, @Nonnull RuleData data) {
        //规则自定义配置
        data.setHeader(RuleConstants.Headers.ruleConfiguration, getJob().getRuleConfiguration());
        //任务执行器标识
        data.setHeader(RuleConstants.Headers.jobExecutor, getJob().getExecutor());
        //模型类型
        data.setHeader(RuleConstants.Headers.modelType, getJob().getModelType());

        log.trace("fire job task [{}] event [{}] ", job, event);

        Mono<T> then = eventBus
            .publish(RuleConstants.Topics.event0(job.getInstanceId(), job.getNodeId(), event), data)
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
        return fireEvent(RuleConstants.Event.error, () -> createErrorData(e, sourceData));
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
            error.put("stack", ExceptionUtils.getStackTrace(e));
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
        if (loadedScope == null) {
            synchronized (this) {
                if (loadedScope == null) {
                    loadedScope = scopeSupplier.get();
                }
            }
        }
        return loadedScope;
    }

    private void init() {
        this.monitor = monitorFactory.apply(job);
        this.logger = Logger.of(monitor.logger());
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
