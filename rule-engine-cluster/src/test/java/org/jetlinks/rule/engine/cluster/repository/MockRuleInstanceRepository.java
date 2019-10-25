package org.jetlinks.rule.engine.cluster.repository;

import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.cluster.persistent.RuleInstancePersistent;
import org.jetlinks.rule.engine.cluster.persistent.repository.RuleInstanceRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class MockRuleInstanceRepository implements RuleInstanceRepository {

    private Map<String, RuleInstancePersistent> repository = new HashMap<>();

    @Override
    public Mono<RuleInstancePersistent> findInstanceById(String instanceId) {
        return Mono.justOrEmpty(repository.get(instanceId));
    }

    @Override
    public Mono<Void> saveInstance(RuleInstancePersistent instancePersistent) {

        return Mono.fromRunnable(() -> {
            repository.put(instancePersistent.getId(), instancePersistent);
        });
    }

    @Override
    public Flux<RuleInstancePersistent> findAll() {
        return Flux.fromIterable(repository.values());
    }

    @Override
    public Mono<Void> changeState(String instanceId, RuleInstanceState state) {
        return findInstanceById(instanceId)
                .map(r -> {
                    r.setState(state);
                    return r;
                })
                .then();
    }
}
