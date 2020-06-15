package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.function.Function;

public class LambdaTaskExecutor extends FunctionTaskExecutor {
    Function<RuleData, Publisher<?>> function;

    public LambdaTaskExecutor(String name,
                              ExecutionContext context,
                              Function<RuleData, Publisher<?>> function) {
        super(name, context);
        this.function = function;
    }

    @Override
    protected Publisher<RuleData> apply(RuleData input) {
        return Flux.from(function.apply(input))
                .map(t -> {
                    if (t instanceof RuleData) {
                        return ((RuleData) t);
                    }
                    return input.newData(t);
                })
                ;
    }
}