package org.jetlinks.rule.engine.api;

import lombok.Getter;
import lombok.Setter;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleInstance {
    private String id;

    private String ruleId;

    private Rule rule;
}
