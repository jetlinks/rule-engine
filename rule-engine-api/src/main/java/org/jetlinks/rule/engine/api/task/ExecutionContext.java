package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scope.ContextScope;
import org.jetlinks.rule.engine.api.scope.FlowScope;
import org.jetlinks.rule.engine.api.scope.GlobalScope;
import org.jetlinks.rule.engine.api.scope.NodeScope;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.function.Supplier;

public interface ExecutionContext {

    String getInstanceId();

    Logger getLogger();

    ScheduleJob getJob();

    <T> Mono<T> fireEvent(@Nonnull String event, @Nonnull RuleData data);

    <T> Mono<T> onError(@Nullable Throwable e, @Nullable RuleData sourceData);

    Input getInput();

    Output getOutput();

    Mono<Void> shutdown(String code, String message);

    RuleData newRuleData(Object data);

    void onShutdown(Runnable runnable);

    boolean isDebug();

    default <T> Mono<T> onError(@NotNull Supplier<Throwable> e, @Nullable RuleData sourceData) {
        return Mono.defer(() -> onError(e.get(), sourceData));
    }

    default ContextScope scope(String id) {
        return flow().context(id);
    }

    default ContextScope scope(RuleData ruleData) {
        return scope(ruleData.getContextId());
    }

    default GlobalScope global() {
        throw new UnsupportedOperationException();
    }

    default NodeScope node() {
        return node(getJob().getNodeId());
    }

    default NodeScope node(String id) {
        return flow().node(id);
    }

    default FlowScope flow() {
        return flow(getInstanceId());
    }

    default FlowScope flow(String id) {
        return global().flow(id);
    }

}
