package org.jetlinks.rule.engine.cluster.repository;

import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.api.persistent.RuleInstancePersistent;
import org.jetlinks.rule.engine.api.persistent.repository.RuleInstanceRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class MockRuleInstanceRepository implements RuleInstanceRepository {

    private Map<String, RuleInstancePersistent> repository = new HashMap<>();

    @Override
    public Optional<RuleInstancePersistent> findInstanceById(String instanceId) {
        return Optional.ofNullable(repository.get(instanceId));
    }

    @Override
    public List<RuleInstancePersistent> findInstanceByRuleId(String ruleId) {
        return repository.values()
                .stream()
                .filter(persistent->persistent.getRuleId().equals(ruleId))
                .collect(Collectors.toList());
    }

    @Override
    public void saveInstance(RuleInstancePersistent instancePersistent) {
        repository.put(instancePersistent.getId(), instancePersistent);
    }

    @Override
    public List<RuleInstancePersistent> findAll() {
        return new ArrayList<>(repository.values());
    }


    @Override
    public List<RuleInstancePersistent> findBySchedulerId(String schedulerId) {
        return findAll()
                .stream()
                .filter(persistent->schedulerId.equals(persistent.getCurrentSchedulerId())||schedulerId.equals(persistent.getSchedulerId()))
                .collect(Collectors.toList());
    }

    @Override
    public void changeState(String instanceId, RuleInstanceState state) {
        findInstanceById(instanceId)
                .map(r -> {
                    r.setState(state);
                    return r;
                })
                .ifPresent(this::saveInstance);
    }
}
