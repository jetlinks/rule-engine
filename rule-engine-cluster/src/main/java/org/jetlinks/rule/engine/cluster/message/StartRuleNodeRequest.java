package org.jetlinks.rule.engine.cluster.message;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class StartRuleNodeRequest implements Serializable {

    private String schedulerNodeId;

    private String instanceId;

    private String nodeId;

    private String ruleId;

    private RuleNodeConfiguration nodeConfig;

    private List<InputConfig> inputQueue = new ArrayList<>();

    private List<OutputConfig> outputQueue = new ArrayList<>();

    private List<EventConfig> eventQueue = new ArrayList<>();

    private Map<String, String> logContext = new HashMap<>();

}
