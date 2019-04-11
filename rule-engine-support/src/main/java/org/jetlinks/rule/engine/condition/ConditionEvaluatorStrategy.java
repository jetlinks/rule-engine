package org.jetlinks.rule.engine.condition;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.Condition;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ConditionEvaluatorStrategy {

    String getType();

    boolean evaluate(Condition condition, RuleData context);

}
