package org.jetlinks.rule.engine.cluster.repository;

import org.jetlinks.rule.engine.api.persistent.RuleInstancePersistent;
import org.jetlinks.rule.engine.api.persistent.repository.RuleInstanceRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    public void stopInstance(String instanceId) {
        findInstanceById(instanceId)
                .ifPresent(persistent -> persistent.setRunning(false));
    }

    @Override
    public void startInstance(String instanceId) {
        findInstanceById(instanceId)
                .ifPresent(persistent -> persistent.setRunning(true));
    }
}
