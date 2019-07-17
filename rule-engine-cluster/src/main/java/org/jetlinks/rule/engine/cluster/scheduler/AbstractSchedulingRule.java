package org.jetlinks.rule.engine.cluster.scheduler;

import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.api.cluster.scheduler.SchedulingRule;

public  abstract class AbstractSchedulingRule implements SchedulingRule {


    protected abstract void setState(RuleInstanceState state);

}
