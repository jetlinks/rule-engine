package org.jetlinks.rule.engine.cluster.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class DistributedRuleDetail implements Serializable {

    private String instanceId;

    private String ruleId;

    private String nodeId;

    private String workerId;

    private String detailJson;

}