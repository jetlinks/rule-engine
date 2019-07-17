package org.jetlinks.rule.engine.api.cluster.scheduler;

import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.api.persistent.RuleInstancePersistent;

import java.util.concurrent.CompletionStage;

/**
 * 调度中的规则
 */
public interface SchedulingRule {

    RuleInstanceState getState();

    RuleInstanceContext getContext();

    CompletionStage<Void> start();

    CompletionStage<Void> stop();

    CompletionStage<Void> init();

    CompletionStage<Void> tryResume(String workerId);

    RuleInstancePersistent toPersistent();
}