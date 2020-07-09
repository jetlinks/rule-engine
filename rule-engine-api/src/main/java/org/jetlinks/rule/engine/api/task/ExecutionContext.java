package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
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

    default <T> Mono<T> onError(@NotNull Supplier<Throwable> e, @Nullable RuleData sourceData) {
        return Mono.defer(() -> onError(e.get(), sourceData));
    }

    Input getInput();

    Output getOutput();

    Mono<Void> shutdown(String code, String message);

    RuleData newRuleData(Object data);

    void onShutdown(Runnable runnable);

    boolean isDebug();
}
