package org.jetlinks.rule.engine.api.cluster.scheduler;

import org.jetlinks.rule.engine.api.Rule;

import java.util.Optional;

public interface RuleEngineScheduler {


    Optional<SchedulingRule> getSchedulingRule(String instanceId);

    SchedulingRule schedule(String instanceId, Rule rule);


}
