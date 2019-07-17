package org.jetlinks.rule.engine.api.persistent.repository;

import org.jetlinks.rule.engine.api.persistent.RulePersistent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RuleRepository {

    Optional<RulePersistent> findRuleById(String ruleId);

    List<RulePersistent> findRuleByIdList(Collection<String> ruleIdList);

    void save(RulePersistent persistent);
}
