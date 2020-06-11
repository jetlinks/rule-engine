package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import org.jetlinks.rule.engine.api.ExecutionContext;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.Task;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public abstract class FunctionTaskExecutor extends AbstractTaskExecutor {

    @Getter
    private final String name;

    public FunctionTaskExecutor(String name, ExecutionContext context) {
        super(context);
        this.name = name;
    }

    protected abstract Publisher<RuleData> apply(RuleData input);

    @Override
    protected Disposable doStart() {
        return context
                .getInput()
                .subscribe()
                .filter(data -> state == Task.State.running)
                .flatMap(input -> context
                        .getOutput()
                        .write(Flux
                                .from(this.apply(input))
                                .flatMap(output -> context
                                        .fireEvent(RuleConstants.Event.result, output)
                                        .thenReturn(output)))
                        .then(context.fireEvent(RuleConstants.Event.complete, input))
                        .onErrorResume(error -> context.onError(error, input)))
                .onErrorResume(error -> context.onError(error, null))
                .subscribe()
                ;
    }


}
