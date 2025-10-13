package org.jetlinks.rule.engine.cluster.worker;

import lombok.AllArgsConstructor;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.lang.SharedPathString;
import org.jetlinks.core.monitor.DefaultMonitor;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.metrics.Metrics;
import org.jetlinks.core.monitor.tracer.SimpleTracer;
import org.jetlinks.core.monitor.tracer.Tracer;
import org.jetlinks.core.utils.RecyclerUtils;
import org.jetlinks.rule.engine.api.CompositeLogger;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.Slf4jLogger;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.defaults.EventLogger;

@AllArgsConstructor
public class DefaultRuleMonitorManager implements RuleMonitorManager {

    protected final String workerId;

    protected final EventBus eventBus;

    @Override
    public Monitor createMonitor(ScheduleJob job) {
        return new DefaultMonitor(
            createLogger(job), createTracer(job), createMetrics(job)
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


}
