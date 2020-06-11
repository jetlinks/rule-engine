package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.executor.Input;
import org.jetlinks.rule.engine.api.executor.Output;
import org.jetlinks.rule.engine.api.executor.ScheduleJob;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Deprecated
public interface ExecutionContext {

    String getInstanceId();

    Logger getLogger();

    ScheduleJob getJob();

    Mono<Void> fireEvent(@Nonnull String event, @Nonnull RuleData data);

    Mono<Void> onError(@Nullable Throwable e, @Nullable RuleData data);

    Input getInput();

    Output getOutput();

    Mono<Void> shutdown(String code, String message);

    void onShutdown(Runnable runnable);

    boolean isDebug();
}
