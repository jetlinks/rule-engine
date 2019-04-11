package org.jetlinks.rule.engine.cluster.executor;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.api.executor.Input;
import org.jetlinks.rule.engine.api.executor.Output;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class DefaultContext implements ExecutionContext {

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
    private BiConsumer<RuleData, Throwable> errorHandler;

    @Getter
    @Setter
    private BiConsumer<String,RuleData> eventHandler;

    private List<Runnable> stopListener = new ArrayList<>();

    @Override
    public void fireEvent(String event, RuleData data) {
        eventHandler.accept(event,data);
    }

    @Override
    public void onError(RuleData data, Throwable e) {
        if (null != errorHandler) {
            errorHandler.accept(data, e);
        } else {
            logger.error("unhandled error", e);
        }
    }

    @Override
    public void stop() {
        input.close();
        stopListener.forEach(Runnable::run);
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public void onStop(Runnable runnable) {
        stopListener.add(runnable);
    }
}
