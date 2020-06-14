package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.Condition;


/**
 * 条件执行器
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface ConditionEvaluator {
    boolean evaluate(Condition condition, RuleData context);
}
