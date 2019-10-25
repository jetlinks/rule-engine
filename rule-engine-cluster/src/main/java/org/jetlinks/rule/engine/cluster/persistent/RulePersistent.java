package org.jetlinks.rule.engine.cluster.persistent;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;

import java.io.Serializable;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RulePersistent implements Serializable {

    private String id;

    private String ruleId;

    private Integer version;

    private String name;

    private String modelFormat;

    private String model;


    public Rule toRule(RuleEngineModelParser parser) {
        Rule rule = new Rule();
        rule.setId(id);
        rule.setVersion(version == null ? 1 : version);
        rule.setModel(parser.parse(modelFormat, model));
        return rule;
    }
}
