package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import org.jetlinks.core.trace.TraceHolder;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.core.utils.RecursiveUtils;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskExecutor;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.function.Function;

public abstract class FunctionTaskExecutor extends AbstractTaskExecutor implements TaskExecutor {

    /**
     * 默认最大递归次数限制.
     * -Drule.engine.max_recursive=0
     */
    static final int DEFAULT_MAX_RECURSIVE = Integer.getInteger("rule.engine.max_recursive", 0);

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
            .onErrorResume(error -> context.onError(error, input))
            .then();
    }

    protected Function<Context, Context> contextWriter() {
        if (maxRecursive() >= 0) {
            return RecursiveUtils
                .validator(
                    "rule:" + context.getInstanceId() + ":" + context.getJob().getNodeId(),
                    maxRecursive());
        }
        return Function.identity();
    }

    protected int maxRecursive() {
        return DEFAULT_MAX_RECURSIVE;
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
