package org.jetlinks.rule.engine.executor.node.spring;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.executor.node.RuleNodeConfig;

@Getter
@Setter
public class SpringEventConfiguration implements RuleNodeConfig {

    private String publishClass;

    private String subscribeClass;

    @Override
    public NodeType getNodeType() {
        return NodeType.MAP;
    }

    @Override
    public void setNodeType(NodeType nodeType) {

    }
}