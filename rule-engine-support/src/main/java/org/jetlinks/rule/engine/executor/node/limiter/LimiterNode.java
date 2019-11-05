package org.jetlinks.rule.engine.executor.node.limiter;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.CommonExecutableRuleNodeFactoryStrategy;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@AllArgsConstructor
public class LimiterNode extends CommonExecutableRuleNodeFactoryStrategy<LimiterConfiguration> {

    private LimiterManager limiterManager;

    @Override
    public Function<RuleData, Mono<Boolean>> createExecutor(ExecutionContext context, LimiterConfiguration config) {
        return ruleData ->
                limiterManager
                        .getLimiter(config.getLimitKey())
                        .tryLimit(config.getTime(), config.getLimit())
                        .filter(s -> !s);
    }

    @Override
    public String getSupportType() {
        return "limiter";
    }
}
