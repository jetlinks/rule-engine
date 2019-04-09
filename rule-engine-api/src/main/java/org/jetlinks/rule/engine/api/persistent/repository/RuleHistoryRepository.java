package org.jetlinks.rule.engine.api.persistent.repository;

import org.jetlinks.rule.engine.api.persistent.RuleHistoryPersistent;

public interface RuleHistoryRepository {
    void save(RuleHistoryPersistent persistent);
}
