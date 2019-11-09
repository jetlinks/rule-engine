package org.jetlinks.rule.engine.executor.node.route;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.CommonExecutableRuleNodeFactoryStrategy;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public class RouteEventNode extends CommonExecutableRuleNodeFactoryStrategy<RouteEventNodeConfiguration> {

    @Override
    @SneakyThrows
    public Function<RuleData, ? extends Publisher<?>> createExecutor(ExecutionContext executionContext, RouteEventNodeConfiguration config) {

       return Mono::just;
    }


    @Override
    public String getSupportType() {
        return "route";
    }


}
