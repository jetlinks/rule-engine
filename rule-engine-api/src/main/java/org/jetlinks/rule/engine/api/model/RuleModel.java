package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 规则模型
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleModel {

    private String id;

    private String name;

    private String description;

    private Map<String, Object> configuration;

    private List<RuleNodeModel> nodes;

}
