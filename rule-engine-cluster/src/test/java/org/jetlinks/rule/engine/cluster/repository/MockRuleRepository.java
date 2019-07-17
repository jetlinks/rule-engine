package org.jetlinks.rule.engine.cluster.repository;

import org.jetlinks.rule.engine.api.persistent.RulePersistent;
import org.jetlinks.rule.engine.api.persistent.repository.RuleRepository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

    @Override
    public List<RulePersistent> findRuleByIdList(Collection<String> ruleIdList) {
        return Collections.emptyList();
    }

    @Override
    public void save(RulePersistent persistent) {

    }
}
