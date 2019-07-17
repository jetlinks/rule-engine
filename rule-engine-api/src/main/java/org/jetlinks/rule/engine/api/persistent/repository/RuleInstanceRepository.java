package org.jetlinks.rule.engine.api.persistent.repository;

import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.api.persistent.RuleInstancePersistent;

import java.util.List;
import java.util.Optional;

public interface RuleInstanceRepository {

    Optional<RuleInstancePersistent> findInstanceById(String instanceId);

    List<RuleInstancePersistent> findInstanceByRuleId(String ruleId);

    List<RuleInstancePersistent> findAll();

    List<RuleInstancePersistent> findBySchedulerId(String schedulerId);

    void saveInstance(RuleInstancePersistent instancePersistent);

    void changeState(String instanceId, RuleInstanceState state);
}
