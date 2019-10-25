package org.jetlinks.rule.engine.cluster.persistent;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 规则调度历史, 唯一性: instanceId+nodeId+
 */
@Getter
@Setter
public class RuleScheduleHistoryPersistent implements Serializable {

    /**
     * 规则ID
     */
    private String ruleId;

    /**
     * 规则实例ID
     */
    private String instanceId;

    /**
     * 节点ID,在分布式任务时生效
     */
    private String nodeId;

    /**
     * 具体执行任务的服务节点
     */
    private String workerId;

    /**
     * 首次执行任务的服务节点
     */
    private String firstWorkerId;

    /**
     * 发起调度的服务节点
     */
    private String schedulerId;

    /**
     * 调度时间
     */
    private Long scheduleTime;

    /**
     * 调度详情JSON
     */
    private String detailJson;

}
