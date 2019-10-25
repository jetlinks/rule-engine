package org.jetlinks.rule.engine.cluster.persistent.repository;

import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.cluster.persistent.RuleInstancePersistent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RuleInstanceRepository {

    Mono<RuleInstancePersistent> findInstanceById(String instanceId);

    Flux<RuleInstancePersistent> findAll();

    Mono<Void> saveInstance(RuleInstancePersistent instancePersistent);

    Mono<Void> changeState(String instanceId, RuleInstanceState state);
}
