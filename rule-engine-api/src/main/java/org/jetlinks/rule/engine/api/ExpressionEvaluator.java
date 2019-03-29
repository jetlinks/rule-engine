package org.jetlinks.rule.engine.api;

import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ExpressionEvaluator {
    Object evaluate(String type, String expression, Map<String, Object> context);
}
