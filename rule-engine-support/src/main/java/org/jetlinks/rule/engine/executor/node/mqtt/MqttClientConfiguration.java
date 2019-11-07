package org.jetlinks.rule.engine.executor.node.mqtt;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.executor.node.RuleNodeConfig;
import org.springframework.util.Assert;

import java.util.List;

@Getter
@Setter
public class MqttClientConfiguration implements RuleNodeConfig {

    private String clientId;

    private PayloadType payloadType = PayloadType.JSON;

    private ClientType[] clientType;

    private List<String> topics;

    @Override
    public NodeType getNodeType() {
        return NodeType.PEEK;
    }

    @Override
    public void setNodeType(NodeType nodeType) {

    }

    @Override
    public void validate() {
        Assert.hasText(clientId,"clientId can not be empty");
        Assert.notNull(clientType,"clientType can not be null");
        Assert.notEmpty(topics,"topics can not be empty");

    }
}
