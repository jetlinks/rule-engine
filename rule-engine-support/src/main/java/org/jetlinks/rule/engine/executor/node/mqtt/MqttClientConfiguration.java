package org.jetlinks.rule.engine.executor.node.mqtt;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.executor.node.RuleNodeConfig;

import java.util.List;

@Getter
@Setter
public class MqttClientConfiguration implements RuleNodeConfig {

    private String clientId;

    private PayloadType payloadType = PayloadType.JSON;

    private List<String> topics;

    @Override
    public NodeType getNodeType() {
        return NodeType.PEEK;
    }

    @Override
    public void setNodeType(NodeType nodeType) {

    }
}
