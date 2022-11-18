package org.jetlinks.rule.engine.cluster.worker;

import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scope.GlobalScope;
import org.jetlinks.rule.engine.api.task.Input;
import org.jetlinks.rule.engine.api.task.Output;

import java.util.Map;

public interface RuleIOManager {

    Input createInput(ScheduleJob job);

    Output createOutput(ScheduleJob job);

    GlobalScope createScope();

    Map<String, Output> createEvent(ScheduleJob job);

}
