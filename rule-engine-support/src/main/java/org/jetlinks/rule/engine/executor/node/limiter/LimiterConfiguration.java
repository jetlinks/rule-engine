package org.jetlinks.rule.engine.executor.node.limiter;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.executor.node.RuleNodeConfig;

@Getter
@Setter
public class LimiterConfiguration implements RuleNodeConfig {

    private long time;

    private long limit;

    private String limitKey;


    public String getLimitKey(RuleData data){

        return limitKey;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.PEEK;
    }

    @Override
    public void setNodeType(NodeType nodeType) {

    }
}
