package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.TaskExecutor;
import org.jetlinks.rule.engine.api.executor.ScheduleJob;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DefaultTask implements Task {

    @Getter
    private final String workerId;

    private final DefaultExecutionContext context;

    private final TaskExecutor executor;

    private long lastStateTime;

    private long startTime;

    public DefaultTask(String workerId,
                       DefaultExecutionContext context,
                       TaskExecutor executor) {
        this.workerId = workerId;
        this.context = context;
        this.executor = executor;
        //监听状态切换事件
        executor.onStateChanged((from, to) -> {
            lastStateTime = System.currentTimeMillis();
            Map<String, Object> data = new HashMap<>();
            data.put("from", from.name());
            data.put("to", to.name());

            context.getEventBus()
                    .publish("/rule/engine/" + workerId + "/" + context.getInstanceId() + "/" + context.getJob().getNodeId() + "/state", Mono.just(data))
                    .doOnError(err -> log.error(err.getMessage(), err))
                    .subscribe();

        });
    }

    @Override
    public String getId() {
        return workerId + ":" + context.getInstanceId() + ":" + context.getJob().getNodeId();
    }

    @Override
    public String getName() {
        return executor.getName();
    }

    @Override
    public ScheduleJob getJob() {
        return context.getJob();
    }

    @Override
    public Mono<Void> setJob(ScheduleJob job) {
        return Mono.fromRunnable(() -> {
            ScheduleJob old = context.getJob();
            context.setJob(job);
            try {
                executor.validate();
            } catch (Throwable e) {
                context.setJob(old);
                throw e;
            }
        });
    }

    @Override
    public Mono<Void> reload() {
        return Mono.fromRunnable(executor::reload);
    }

    @Override
    public Mono<Void> start() {
        return Mono.<Void>fromRunnable(executor::start)
                .doOnSuccess((v) -> startTime = System.currentTimeMillis());
    }

    @Override
    public Mono<Void> pause() {
        return Mono.fromRunnable(executor::pause);
    }

    @Override
    public Mono<Void> shutdown() {
        return Mono
                .fromRunnable(executor::shutdown)
                .then(Mono.fromRunnable(context::doShutdown));
    }

    @Override
    public Mono<Void> execute(Publisher<RuleData> data) {
        return context
                .getEventBus()
                .publish("/rule/engine/" + getJob().getInstanceId() + "/" + getJob().getNodeId() + "/input", data)
                .then();
    }

    @Override
    public Mono<State> getState() {
        return Mono.just(executor.getState());
    }

    @Override
    public Mono<Void> debug(boolean debug) {
        return Mono.fromRunnable(() -> context.setDebug(debug));
    }

    @Override
    public long getLastStateTime() {
        return lastStateTime;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }
}
