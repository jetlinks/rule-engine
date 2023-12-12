package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.core.trace.TraceHolder;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ExecutableTaskExecutor;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DefaultTask implements Task {

    //工作器ID
    @Getter
    private final String workerId;

    //调度器ID
    @Getter
    private final String schedulerId;

    private final AbstractExecutionContext context;

    private final TaskExecutor executor;

    private long lastStateTime;

    private long startTime;

    private final String id;

    public DefaultTask(String schedulerId,
                       String workerId,
                       AbstractExecutionContext context,
                       TaskExecutor executor) {
        this.schedulerId = schedulerId;
        this.workerId = workerId;
        this.context = context;
        this.executor = executor;
        this.id = DigestUtils.md5Hex(workerId + ":" + context.getInstanceId() + ":" + context.getJob().getNodeId());
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
                               .publish(RuleConstants.Topics.state(context.getInstanceId(), context
                                       .getJob()
                                       .getNodeId()), data),
                        context.getEventBus()
                               .publish(RuleConstants.Topics.event(context.getInstanceId(), context
                                       .getJob()
                                       .getNodeId(), to.name()), context.newRuleData(data))
                )
                .doOnError(err -> log.error(err.getMessage(), err))
                .subscribe();

        });
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return executor.getName();
    }

    /**
     * 获取任务信息
     *
     * @return 任务信息
     */
    @Override
    public ScheduleJob getJob() {
        return context.getJob();
    }

    /**
     * 设置任务信息,用于热更新任务.
     *
     * @param job 任务信息
     * @return empty Mono
     */
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

    /**
     * 重新加载任务,如果配置发生变化,将重启任务.
     *
     * @return empty Mono
     */
    @Override
    public Mono<Void> reload() {
        log.debug("reload task[{}]:[{}]", getId(), getJob());
        return Mono
                .<Void>fromRunnable(() -> {
                    context.reload();
                    executor.reload();
                })
                .as(MonoTracer.create(
                        RuleConstants.Trace.reloadNodeSpanName(getJob().getInstanceId(), getJob().getNodeId()),
                        builder -> builder.setAttribute(RuleConstants.Trace.executor, getJob().getExecutor())))
                .subscribeOn(Schedulers.boundedElastic());
    }


    /**
     * 启动,开始执行任务
     *
     * @return empty Mono
     */
    @Override
    public Mono<Void> start() {
        log.debug("start task[{}]:[{}]", getId(), getJob());
        return Mono.<Void>fromRunnable(executor::start)
                   .doOnSuccess((v) -> startTime = System.currentTimeMillis())
                   .as(MonoTracer.create(
                           RuleConstants.Trace.startNodeSpanName(getJob().getInstanceId(), getJob().getNodeId()),
                           builder -> builder.setAttribute(RuleConstants.Trace.executor, getJob().getExecutor())))
                   .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 暂停执行任务
     *
     * @return empty Mono
     */
    @Override
    public Mono<Void> pause() {
        log.debug("pause task[{}]:[{}]", getId(), getJob());
        return Mono.fromRunnable(executor::pause);
    }

    /**
     * 停止任务,与暂停不同等的是,停止后将进行清理资源等操作
     *
     * @return empty Mono
     */
    @Override
    public Mono<Void> shutdown() {
        log.debug("shutdown task[{}]:[{}]", getId(), getJob());
        return Mono
                .fromRunnable(executor::shutdown)
                .then(Mono.<Void>fromRunnable(context::doShutdown))
                .as(MonoTracer.create(
                        RuleConstants.Trace.shutdownNodeSpanName(getJob().getInstanceId(), getJob().getNodeId()),
                        builder -> builder.setAttribute(RuleConstants.Trace.executor, getJob().getExecutor())))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 执行任务
     *
     * @return 结果
     */
    @Override
    public Mono<Void> execute(RuleData data) {
        log.debug("execute task[{}]:[{}]", getId(), getJob());
        if (executor instanceof ExecutableTaskExecutor) {
            return TraceHolder
                    .writeContextTo(data, RuleData::setHeader)
                    .flatMap(ruleData -> ((ExecutableTaskExecutor) executor).execute(ruleData));
        }
        return Mono.empty();
    }

    /**
     * 获取任务状态
     *
     * @return 状态
     */
    @Override
    public Mono<State> getState() {
        return Mono.just(executor.getState());
    }

    /**
     * 设置debug,开启debug后,会打印更多的日志信息。
     *
     * @param debug 是否开启debug
     * @return empty Mono
     */
    @Override
    public Mono<Void> debug(boolean debug) {
        log.debug("set task debug[{}] [{}]:[{}]", debug, getId(), getJob());
        return Mono.fromRunnable(() -> context.setDebug(debug));
    }

    /**
     * @return 上一次状态变更时间
     */
    @Override
    public Mono<Long> getLastStateTime() {
        return Mono.just(lastStateTime);
    }

    /**
     * @return 启动时间
     */
    @Override
    public Mono<Long> getStartTime() {
        return Mono.just(startTime);
    }
}
