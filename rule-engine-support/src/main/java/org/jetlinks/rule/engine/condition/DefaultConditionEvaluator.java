package org.jetlinks.rule.engine.condition;

import org.apache.commons.collections4.MapUtils;
import org.hswebframework.web.exception.I18nSupportException;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.Condition;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultConditionEvaluator implements ConditionEvaluator {

    private final Map<String, ConditionEvaluatorStrategy> allStrategy = new HashMap<>();

    @Override
    public boolean evaluate(Condition condition, RuleData context) {
        if (condition == null || MapUtils.isEmpty(condition.getConfiguration())) {
            return true;
        }
        return Optional
            .ofNullable(allStrategy.get(condition.getType()))
            .map(strategy -> strategy.evaluate(condition, context))
            .orElseThrow(() -> new I18nSupportException.NoStackTrace("error.rule.unsupported_condition_type", condition.getType()));
    }

    @Override
    public Function<RuleData, Mono<Boolean>> prepare(Condition condition) {
        if (condition == null || MapUtils.isEmpty(condition.getConfiguration())) {
            return ignore -> Reactors.ALWAYS_TRUE;
        }
        return Optional
            .ofNullable(allStrategy.get(condition.getType()))
            .map(strategy -> strategy.prepare(condition))
            .orElseThrow(() -> new I18nSupportException.NoStackTrace("error.rule.unsupported_condition_type", condition.getType()));
    }

    public void register(ConditionEvaluatorStrategy strategy) {
        allStrategy.put(strategy.getType(), strategy);
    }
}
