package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DefaultTask implements Task {

    @Getter
    private final String workerId;

    @Getter
    private final String schedulerId;

    private final DefaultExecutionContext context;

    private final TaskExecutor executor;

    private long lastStateTime;

    private long startTime;

    public DefaultTask(String schedulerId,
                       String workerId,
                       DefaultExecutionContext context,
                       TaskExecutor executor) {
        this.schedulerId = schedulerId;
        this.workerId = workerId;
        this.context = context;
        this.executor = executor;
        //监听状态切换事件
        executor.onStateChanged((from, to) -> {
            lastStateTime = System.currentTimeMillis();
            Map<String, Object> data = new HashMap<>();
            data.put("from", from.name());
            data.put("to", to.name());
            data.put("taskId", getId());
            data.put("instanceId", context.getInstanceId());
            data.put("nodeId", context.getJob().getNodeId());
            data.put("timestamp", System.currentTimeMillis());

            Flux.merge(
                    context.getEventBus()
                            .publish(RuleConstants.Topics.state(context.getInstanceId(), context.getJob().getNodeId()), data),
                    context.getEventBus()
                            .publish(RuleConstants.Topics.event(context.getInstanceId(), context.getJob().getNodeId(), to.name()), RuleData.create(data))
            )
                    .doOnError(err -> log.error(err.getMessage(), err))
                    .subscribe();

        });
    }

    @Override
    public String getId() {
        return DigestUtils.md5Hex(workerId + ":" + context.getInstanceId() + ":" + context.getJob().getNodeId());
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
        log.debug("reload task[{}]:[{}]", getId(), getJob());
        return Mono.fromRunnable(executor::reload);
    }

    @Override
    public Mono<Void> start() {
        log.debug("start task[{}]:[{}]", getId(), getJob());
        return Mono.<Void>fromRunnable(executor::start)
                .doOnSuccess((v) -> startTime = System.currentTimeMillis());
    }

    @Override
    public Mono<Void> pause() {
        log.debug("pause task[{}]:[{}]", getId(), getJob());
        return Mono.fromRunnable(executor::pause);
    }

    @Override
    public Mono<Void> shutdown() {
        log.debug("shutdown task[{}]:[{}]", getId(), getJob());
        return Mono
                .fromRunnable(executor::shutdown)
                .then(Mono.fromRunnable(context::doShutdown));
    }

    @Override
    public Mono<Void> execute(RuleData data) {
        log.debug("execute task[{}]:[{}]", getId(), getJob());
        return context
                .getEventBus()
                .publish(RuleConstants.Topics.input(getJob().getInstanceId(), getJob().getNodeId()), data)
                .then();
    }

    @Override
    public Mono<State> getState() {
        return Mono.just(executor.getState());
    }

    @Override
    public Mono<Void> debug(boolean debug) {
        log.debug("set task debug[{}] [{}]:[{}]", debug, getId(), getJob());
        return Mono.fromRunnable(() -> context.setDebug(debug));
    }

    @Override
    public Mono<Long> getLastStateTime() {
        return Mono.just(lastStateTime);
    }

    @Override
    public Mono<Long> getStartTime() {
        return Mono.just(startTime);
    }
}
