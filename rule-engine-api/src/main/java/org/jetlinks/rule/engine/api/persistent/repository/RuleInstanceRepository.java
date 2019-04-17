package org.jetlinks.rule.engine.api.persistent.repository;

import org.jetlinks.rule.engine.api.persistent.RuleInstancePersistent;

import java.util.List;
import java.util.Optional;

public interface RuleInstanceRepository {

    Optional<RuleInstancePersistent> findInstanceById(String instanceId);

    List<RuleInstancePersistent> findInstanceByRuleId(String ruleId);

    void saveInstance(RuleInstancePersistent instancePersistent);

    void stopInstance(String instanceId);

    void startInstance(String instanceId);

}
