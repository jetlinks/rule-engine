package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.TaskExecutor;
import org.jetlinks.rule.engine.api.task.TaskExecutorProvider;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public class LambdaTaskExecutorProvider implements TaskExecutorProvider {
    private final Function<RuleData, Publisher<RuleData>> function;

    private final String executor;

    private final String name;

    public LambdaTaskExecutorProvider(String executor, Function<RuleData, Publisher<RuleData>> function) {
        this(executor, executor, function);
    }

    public LambdaTaskExecutorProvider(String executor, String name, Function<RuleData, Publisher<RuleData>> function) {
        this.function = function;
        this.executor = executor;
        this.name = name;
    }

    @Override
    public String getExecutor() {
        return executor;
    }

    @Override
    public Mono<TaskExecutor> createTask(ExecutionContext context) {
        return Mono.just(new LambdaTaskExecutor(name, context));
    }


    private class LambdaTaskExecutor extends FunctionTaskExecutor {

        public LambdaTaskExecutor(String name, ExecutionContext context) {
            super(name, context);
        }

        @Override
        protected Publisher<RuleData> apply(RuleData input) {
            return function.apply(input);
        }
    }
}
