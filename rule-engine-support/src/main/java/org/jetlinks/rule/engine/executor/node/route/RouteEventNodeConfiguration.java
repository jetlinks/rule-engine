package org.jetlinks.rule.engine.executor.node.route;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.executor.node.RuleNodeConfig;

@Getter
@Setter
public class RouteEventNodeConfiguration implements RuleNodeConfig {


    @Override
    public NodeType getNodeType() {
        return NodeType.PEEK;
    }

    @Override
    public void setNodeType(NodeType nodeType) {

    }
}