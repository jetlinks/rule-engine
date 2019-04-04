package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.model.Condition;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ConditionEvaluator {
    Object evaluate(Condition condition, RuleData context);
}
