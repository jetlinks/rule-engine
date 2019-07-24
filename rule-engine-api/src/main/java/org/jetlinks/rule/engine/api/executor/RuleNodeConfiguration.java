package org.jetlinks.rule.engine.api.executor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;

import java.io.Serializable;
import java.util.Map;

/**
 * 规则节点配置
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
@EqualsAndHashCode
public class RuleNodeConfiguration implements Serializable {

    /**
     * 节点标识
     */
    private String id;

    /**
     * 节点ID
     *
     * @see RuleNodeModel#getId()
     */
    private String nodeId;

    /**
     * 节点名称
     *
     * @see RuleNodeModel#getName()
     */
    private String name;

    /**
     * 节点执行器
     *
     * @see ExecutableRuleNode
     */
    private String executor;

    /**
     * 节点执行器的配置信息
     */
    private Map<String, Object> configuration;

    /**
     * 节点类型.
     */
    private NodeType nodeType;

}
