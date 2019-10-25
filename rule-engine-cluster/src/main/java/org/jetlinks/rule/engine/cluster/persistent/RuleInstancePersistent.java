package org.jetlinks.rule.engine.cluster.persistent;

import lombok.*;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;

import java.io.Serializable;
import java.util.Date;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleInstancePersistent implements Serializable {
    private String id;

    private String ruleId;

    private Date createTime;

    private int version;

    private String modelFormat;

    private String model;

    private boolean enabled;

    private RuleInstanceState state;

    public Rule toRule(RuleEngineModelParser parser) {
        Rule rule = new Rule();
        rule.setId(ruleId);
        rule.setVersion(version);
        rule.setModel(parser.parse(modelFormat, model));
        return rule;
    }
}
