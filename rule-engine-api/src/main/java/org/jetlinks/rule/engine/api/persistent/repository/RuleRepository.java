package org.jetlinks.rule.engine.api.persistent.repository;

import org.jetlinks.rule.engine.api.persistent.RulePersistent;

import java.util.Optional;

public interface RuleRepository {

    Optional<RulePersistent> findRuleById(String ruleId);




}
