package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskExecutor;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class FunctionTaskExecutor extends AbstractTaskExecutor implements TaskExecutor {

    @Getter
    private final String name;

    public FunctionTaskExecutor(String name, ExecutionContext context) {
        super(context);
        this.name = name;
    }

    protected abstract Publisher<RuleData> apply(RuleData input);

    private Mono<Void> doApply(RuleData input) {

        return Flux
            .from(this.apply(input))
            .concatMap(data -> context
                .fireEvent(RuleConstants.Event.result, data)
                .then(context.getOutput().write(data)), 0)
            .then(context.fireEvent(RuleConstants.Event.complete, input))
            .contextWrite(contextWriter())
            .as(tracer())
            .onErrorResume(error -> {
                context.logger().warn(error.getLocalizedMessage(),error);
                return context.onError(error, input);
            })
            .then();
    }

    @Override
    public final Mono<Void> execute(RuleData ruleData) {
        return doApply(context.newRuleData(ruleData));
    }

    @Override
    protected Disposable doStart() {
        return context
            .getInput()
            .accept(data -> {
                if (state != Task.State.running) {
                    return Mono.empty();
                }
                return this
                    .doApply(data)
                    .then(Reactors.ALWAYS_TRUE);
            })
            ;
    }


}
