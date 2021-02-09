package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则连线,通过连接将不同的节点组合起来
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleLink {

    /**
     * 连线ID
     */
    private String id;

    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 连线类型,在监听事件时,连线表示事件类型
     */
    private String type;

    /**
     * 连线配置
     */
    private Map<String, Object> configuration = new HashMap<>();

    /**
     * 条件,source节点输出的数据需要满足条件才会输出到target时
     */
    private Condition condition;

    /**
     * 连线的源节点
     */
    private RuleNodeModel source;

    /**
     * 连线的目标节点
     */
    private RuleNodeModel target;

}
