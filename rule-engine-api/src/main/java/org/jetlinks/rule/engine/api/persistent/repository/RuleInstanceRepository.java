package org.jetlinks.rule.engine.api.persistent.repository;

import org.jetlinks.rule.engine.api.persistent.RuleInstancePersistent;

import java.util.Optional;

public interface RuleInstanceRepository {

    Optional<RuleInstancePersistent> findInstanceById(String instanceId);

    void saveInstance(RuleInstancePersistent instancePersistent);

    void stopInstance(String instanceId);

    void startInstance(String instanceId);

}
