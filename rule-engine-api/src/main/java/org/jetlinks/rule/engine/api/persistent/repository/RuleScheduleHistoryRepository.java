package org.jetlinks.rule.engine.api.persistent.repository;

import org.jetlinks.rule.engine.api.persistent.RuleScheduleHistoryPersistent;

import java.util.List;

public interface RuleScheduleHistoryRepository {

    List<RuleScheduleHistoryPersistent> findByInstanceId(String instanceId);

    List<RuleScheduleHistoryPersistent> findByWorkerId(String workerId);

    void save(RuleScheduleHistoryPersistent history);


}
