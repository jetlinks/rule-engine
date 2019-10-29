package org.jetlinks.rule.engine.cluster.executor;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.api.executor.Input;
import org.jetlinks.rule.engine.api.executor.Output;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class DefaultContext implements ExecutionContext {

    @Getter
    @Setter
    private String instanceId;

    @Getter
    @Setter
    private String nodeId;

    @Getter
    @Setter
    private Input input;

    @Getter
    @Setter
    private Output output;

    @Getter
    @Setter
    private Logger logger;

    @Getter
    @Setter
    private BiFunction<RuleData, Throwable, Mono<Void>> errorHandler;

    @Getter
    @Setter
    private BiFunction<String, RuleData, Mono<Void>> eventHandler;

    private List<Runnable> stopListener = new ArrayList<>();

    @Override
    public Mono<Void> fireEvent(String event, RuleData data) {
        return Mono.defer(() -> eventHandler.apply(event, data.copy()));
    }

    @Override
    public Mono<Void> onError(RuleData data, Throwable e) {
        return Mono.defer(() -> {
            if (null != errorHandler) {
                return errorHandler.apply(data.copy(), e);
            } else {
                logger.error("unhandled error", e);
            }
            return Mono.empty();
        });
    }

    @Override
    public synchronized void stop() {
        input.close();
        stopListener.forEach(Runnable::run);
        stopListener.clear();
    }


    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public synchronized void onStop(Runnable runnable) {
        stopListener.add(runnable);
    }
}
