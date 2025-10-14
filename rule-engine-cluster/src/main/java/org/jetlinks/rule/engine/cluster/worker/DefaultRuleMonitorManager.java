package org.jetlinks.rule.engine.cluster.worker;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.lang.SharedPathString;
import org.jetlinks.core.metadata.types.BooleanType;
import org.jetlinks.core.monitor.DefaultMonitor;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.metrics.Metrics;
import org.jetlinks.core.monitor.recorder.AbstractActionRecorder;
import org.jetlinks.core.monitor.recorder.ActionRecord;
import org.jetlinks.core.monitor.recorder.ActionRecorder;
import org.jetlinks.core.monitor.recorder.Recorder;
import org.jetlinks.core.monitor.tracer.SimpleTracer;
import org.jetlinks.core.monitor.tracer.Tracer;
import org.jetlinks.core.utils.RecyclerUtils;
import org.jetlinks.rule.engine.api.CompositeLogger;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.Slf4jLogger;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.cluster.monitor.TaskActionRecord;
import org.jetlinks.rule.engine.defaults.EventLogger;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Objects;

@AllArgsConstructor
public class DefaultRuleMonitorManager implements RuleMonitorManager {

    protected final String workerId;

    protected final EventBus eventBus;

    @Override
    public Monitor createMonitor(ScheduleJob job) {
        return new DefaultMonitor(
            createLogger(job),
            createTracer(job),
            createMetrics(job),
            createRecorder(job)
        );
    }

    protected Metrics createMetrics(ScheduleJob job) {
        return Metrics.noop();
    }

    protected Tracer createTracer(ScheduleJob job) {
        return new SimpleTracer(createSpanName(job));
    }

    protected Logger createLogger(ScheduleJob job) {
        Logger logger = new EventLogger(eventBus, job.getInstanceId(), job.getNodeId(), workerId);
        if (Slf4jLogger.isEnabled()) {
            logger = CompositeLogger.of(logger, new Slf4jLogger(logger.getName()));
        }
        return logger;
    }

    protected Recorder createRecorder(ScheduleJob job) {
        // 需要主动开启记录.
        boolean enabled = job
            .getRuleConfiguration(RuleConstants.ConfigKey.enableRecorder)
            .map(BooleanType.GLOBAL::convert)
            .orElse(false);
        if (!enabled) {
            return Recorder.noop();
        }
        return new TaskRecorder(eventBus, job);
    }


    protected SharedPathString createSpanName(ScheduleJob job) {
        //  /rule-runtime/{executor}/{ruleInstanceId}/{nodeId}
        return SharedPathString
            .of(new String[]{
                "",
                "rule-runtime",
                RecyclerUtils.intern(job.getExecutor()),
                RecyclerUtils.intern(job.getInstanceId()),
                RecyclerUtils.intern(job.getNodeId())
            });
    }

    protected static class TaskRecorder implements Recorder {
        final EventBus eventBus;
        final ScheduleJob job;

        TaskRecorder(EventBus eventBus, ScheduleJob job) {
            this.eventBus = eventBus;
            this.job = job;
        }

        @Override
        public <E> ActionRecorder<E> action(@Nonnull CharSequence action) {
            return new TaskActionRecorder<>(Objects.requireNonNull(action, "action can not be null"), eventBus, job);
        }
    }

    protected static class TaskActionRecorder<E> extends AbstractActionRecorder<E> {
        private final EventBus eventBus;
        private final ScheduleJob job;
        private ContextView ctx;

        public TaskActionRecorder(CharSequence name, EventBus eventBus, ScheduleJob job) {
            super(name);
            this.eventBus = eventBus;
            this.job = job;
        }

        public TaskActionRecorder(CharSequence name, String parentId, EventBus eventBus, ScheduleJob job) {
            super(name, parentId);
            this.eventBus = eventBus;
            this.job = job;
        }

        @Override
        protected ActionRecord newRecord() {
            return new TaskActionRecord();
        }

        @Override
        protected void handle(ActionRecord record) {
            TaskActionRecord _record = ((TaskActionRecord) record);
            _record.setExecutor(job.getExecutor());
            _record.setInstanceId(job.getInstanceId());

            _record.setNodeId(job.getNodeId());
            _record.setNodeName(job.getName());
            eventBus
                .publish(
                    // /rule-engine/*/*/action/{...}
                    RuleConstants
                        .Topics
                        .action(
                            job.getInstanceId(),
                            job.getNodeId(),
                            record.getAction()
                        ),
                    record)
                .subscribe(
                    null,
                    null,
                    null,
                    Context.of(ctx));
        }

        @Override
        public ActionRecorder<E> start(ContextView context) {
            this.ctx = context;
            return super.start(context);
        }

        @Override
        public <T> ActionRecorder<T> child(CharSequence action) {
            return new TaskActionRecorder<T>(action, record.getId(), eventBus, job)
                .tag(RuleConstants.Tags.contextId,
                     MapUtils.getString(record.getTags(),RuleConstants.Tags.contextId));
        }
    }

}
