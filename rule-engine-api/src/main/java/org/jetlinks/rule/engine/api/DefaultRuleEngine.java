package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.model.RuleModel;
import reactor.core.publisher.Mono;


public class DefaultRuleEngine {

    //调度器
    private Scheduler scheduler;

    public Mono<Void> start(RuleModel model){

        return Mono.empty();

    }

}
