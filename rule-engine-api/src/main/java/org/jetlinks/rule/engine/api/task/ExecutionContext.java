package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
