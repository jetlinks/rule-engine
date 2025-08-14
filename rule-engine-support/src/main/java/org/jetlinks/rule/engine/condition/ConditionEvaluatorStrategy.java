package org.jetlinks.rule.engine.condition;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.Condition;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * 条件执行策略,实现此接口来拓展规则节点执行条件.
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface ConditionEvaluatorStrategy {

    /**
     * 策略类型标识
     *
     * @return 策略类型标识
     */
    String getType();

    /**
     * 执行策略
     *
     * @param condition 条件配置
     * @param context   规则数据
     * @return 是否匹配
     * @deprecated {@link ConditionEvaluatorStrategy#prepare(Condition)}
     */
    @Deprecated
    boolean evaluate(Condition condition, RuleData context);

    /**
     * 预编译处理条件,返回一个函数,调用这个函数{@link  Function#apply(Object)}来执行判断逻辑
     *
     * @param condition 条件配置
     * @return 判断函数
     */
    default Function<RuleData, Mono<Boolean>> prepare(Condition condition) {
        return (data) -> Mono.just(evaluate(condition, data));
    }
}
