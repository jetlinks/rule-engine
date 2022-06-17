package org.jetlinks.rule.engine.condition;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.Condition;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ConditionEvaluatorStrategy {

    String getType();

    boolean evaluate(Condition condition, RuleData context);

    /**
     * prepare
     * @param condition prepare
     * @return prepare
     */
    default Function<RuleData, Mono<Boolean>> prepare(Condition condition) {
        return (data) -> Mono.just(evaluate(condition, data));
    }
}
