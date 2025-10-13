package org.jetlinks.rule.engine.cluster.worker;

import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;

public interface RuleMonitorManager {

    Monitor createMonitor(ScheduleJob job);

}
