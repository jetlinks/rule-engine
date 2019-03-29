package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleLink {

    private String id;

    private String name;

    private String description;

    private Map<String, Object> configuration;

    private Condition condition;

    private List<RuleNodeModel> source;

    private List<RuleNodeModel> target;

}
