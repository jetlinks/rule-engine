package org.jetlinks.rule.engine.condition;

import org.jetlinks.rule.engine.api.task.ConditionEvaluator;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.Condition;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultConditionEvaluator implements ConditionEvaluator {

    private final Map<String, ConditionEvaluatorStrategy> allStrategy = new HashMap<>();

    @Override
    public boolean evaluate(Condition condition, RuleData context) {
        if (condition == null || StringUtils.isEmpty(condition)) {
            return true;
        }
        return Optional.ofNullable(allStrategy.get(condition.getType()))
                .map(strategy -> strategy.evaluate(condition, context))
                .orElseThrow(() -> new UnsupportedOperationException("不支持的条件类型:" + condition.getType()));
    }

    public void register(ConditionEvaluatorStrategy strategy) {
        allStrategy.put(strategy.getType(), strategy);
    }
}
