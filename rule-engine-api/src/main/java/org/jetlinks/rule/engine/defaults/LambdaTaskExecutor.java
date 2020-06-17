package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.function.Function;
import java.util.function.Supplier;

public class LambdaTaskExecutor extends FunctionTaskExecutor {
    Supplier<Function<RuleData, Publisher<?>>> supplier;

    Function<RuleData, Publisher<?>> function;

    public LambdaTaskExecutor(String name,
                              ExecutionContext context,
                              Function<RuleData, Publisher<?>> function) {
        this(name, context, () -> function);

    }

    public LambdaTaskExecutor(String name,
                              ExecutionContext context,
                              Supplier<Function<RuleData, Publisher<?>>> supplier) {
        super(name, context);
        this.supplier = supplier;
        this.function = this.supplier.get();
    }

    @Override
    public void reload() {
        super.reload();
        this.function = this.supplier.get();
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