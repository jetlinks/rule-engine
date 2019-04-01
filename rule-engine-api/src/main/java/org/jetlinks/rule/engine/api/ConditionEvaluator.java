package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.api.model.Condition;

import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ConditionEvaluator {
    Object evaluate(Condition condition, Map<String, Object> context);
}
