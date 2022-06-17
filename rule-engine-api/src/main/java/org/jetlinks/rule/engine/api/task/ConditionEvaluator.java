package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.Condition;
import reactor.core.publisher.Mono;

import java.util.function.Function;


/**
 * 条件执行器，用于根据条件和规则数据来判断条件是否满足
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface ConditionEvaluator {
    /**
     * 执行并返回是否满足条件
     *
     * @param condition 条件
     * @param context   规则数据
     * @return 是否满足条件
     */
    @Deprecated
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
