package org.jetlinks.rule.engine.api;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.cluster.RunMode;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import org.jetlinks.rule.engine.api.model.RuleModel;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class Rule {
    private String id;

    private int version;

    private RuleModel model;
}
