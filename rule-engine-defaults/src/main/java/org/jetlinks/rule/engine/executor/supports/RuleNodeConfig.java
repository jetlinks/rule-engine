package org.jetlinks.rule.engine.executor.supports;

import org.jetlinks.rule.engine.api.model.NodeType;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleNodeConfig {
    NodeType getNodeType();

    void setNodeType(NodeType nodeType);
}
