package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.ExecutionContext;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.TaskExecutor;
import org.jetlinks.rule.engine.api.TaskExecutorProvider;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public class MockTaskExecutorProvider implements TaskExecutorProvider {

    private final Function<RuleData, Publisher<RuleData>> function;

    private final String executor;

    public MockTaskExecutorProvider(String executor, Function<RuleData, Publisher<RuleData>> function) {
        this.function = function;
        this.executor = executor;
    }

    @Override
    public String getExecutor() {
        return executor;
    }

    @Override
    public Mono<TaskExecutor> createTask(ExecutionContext context) {
        return Mono.just(new MockTaskExecutor(executor, context));
    }


    private class MockTaskExecutor extends FunctionTaskExecutor {

        public MockTaskExecutor(String name, ExecutionContext context) {
            super(name, context);
        }

        @Override
        protected Publisher<RuleData> apply(RuleData input) {
            return function.apply(input);
        }
    }

}
