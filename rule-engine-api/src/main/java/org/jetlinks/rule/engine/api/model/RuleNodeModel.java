package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 规则节点
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleNodeModel {

    private String id;

    private String name;

    private String description;

    private String type;

    private Map<String, Object> configuration;

    private List<RuleLink> inputs;

    private List<RuleLink> outputs;

}
