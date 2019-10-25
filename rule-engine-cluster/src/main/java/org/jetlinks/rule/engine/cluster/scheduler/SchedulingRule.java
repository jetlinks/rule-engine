package org.jetlinks.rule.engine.cluster.scheduler;

import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.RuleInstanceState;
import reactor.core.publisher.Mono;

/**
 * 调度中的规则
 */
public interface SchedulingRule {

    RuleInstanceState getState();

    RuleInstanceContext getContext();

    Mono<Boolean> start();

    Mono<Boolean> stop();

    Mono<Boolean> init();

    Mono<Boolean> tryResume(String workerId);

}