package org.jetlinks.rule.engine.cluster.repository;

import org.jetlinks.rule.engine.api.persistent.RulePersistent;
import org.jetlinks.rule.engine.api.persistent.repository.RuleRepository;

import java.util.Optional;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class MockRuleRepository implements RuleRepository {
    @Override
    public Optional<RulePersistent> findRuleById(String ruleId) {
        return Optional.empty();
    }
}
