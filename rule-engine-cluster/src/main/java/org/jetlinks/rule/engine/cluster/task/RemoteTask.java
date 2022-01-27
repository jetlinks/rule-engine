package org.jetlinks.rule.engine.cluster.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskSnapshot;
import org.jetlinks.rule.engine.cluster.scheduler.SchedulerRpcService;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class RemoteTask implements Task {
    @Getter
    private final String id;

    @Getter
    private final String name;

    @Getter
    private final String workerId;

    @Getter
    private final String schedulerId;

    @Getter
    private final SchedulerRpcService rpcService;

    /**
     * 获取任务信息,请勿修改此任务信息的属性,修改了也没用。
     *
     */
    @Getter
    private ScheduleJob job;

    /**
     * 设置任务信息,用于热更新任务.
     *
     * @param job 任务信息
     * @return empty Mono
     */
    @Override
    public Mono<Void> setJob(ScheduleJob job) {
        this.job = job;
        return rpcService
                .setTaskJob(id,job);
    }

    private Mono<Void> operation(SchedulerRpcService.TaskOperation operation) {
        return rpcService
                .taskOperation(id,operation);
    }

    /**
     * 重新加载任务,如果配置发生变化,将重启任务.
     *
     * @return empty Mono
     */
    @Override
    public Mono<Void> reload() {
        return operation(SchedulerRpcService.TaskOperation.RELOAD);
    }

    /**
     * 启动,开始执行任务
     *
     * @return empty Mono
     */
    @Override
    public Mono<Void> start() {
        return operation(SchedulerRpcService.TaskOperation.START);
    }

    /**
     * 暂停执行任务
     *
     * @return empty Mono
     */
    @Override
    public Mono<Void> pause() {
        return operation(SchedulerRpcService.TaskOperation.PAUSE);
    }

    /**
     * 停止任务,与暂停不同等的是,停止后将进行清理资源等操作
     *
     * @return empty Mono
     */
    @Override
    public Mono<Void> shutdown() {
        return operation(SchedulerRpcService.TaskOperation.SHUTDOWN);
    }

    /**
     * 执行任务
     *
     * @return 结果
     */
    @Override
    public Mono<Void> execute(RuleData data) {
        return rpcService.executeTask(id, data).then();
    }

    /**
     * 获取任务状态
     *
     * @return 状态
     */
    @Override
    public Mono<State> getState() {
        return rpcService
                .getTaskState(id);
    }

    /**
     * 设置debug,开启debug后,会打印更多的日志信息。
     *
     * @param debug 是否开启debug
     * @return empty Mono
     */
    @Override
    public Mono<Void> debug(boolean debug) {
        return operation(debug ? SchedulerRpcService.TaskOperation.ENABLE_DEBUG : SchedulerRpcService.TaskOperation.DISABLE_DEBUG);
    }

    /**
     * @return 上一次状态变更时间
     */
    @Override
    public Mono<Long> getLastStateTime() {
        return rpcService.getLastStateTime(id);
    }

    /**
     * @return 启动时间
     */
    @Override
    public Mono<Long> getStartTime() {
        return rpcService.getStartTime(id);
    }

    @Override
    public Mono<TaskSnapshot> dump() {
        return rpcService.dumpTask(id);
    }
}
