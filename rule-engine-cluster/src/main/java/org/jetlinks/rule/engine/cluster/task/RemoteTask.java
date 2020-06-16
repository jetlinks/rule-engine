package org.jetlinks.rule.engine.cluster.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.Task;
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

    @Getter
    private ScheduleJob job;

    @Override
    public Mono<Void> setJob(ScheduleJob job) {
        this.job = job;
        return rpcService
                .setTaskJob(id,job);
    }

    private Mono<Void> operation(TaskRpc.TaskOperation operation) {
        return rpcService
                .taskOperation(id,operation);
    }

    @Override
    public Mono<Void> reload() {
        return operation(TaskRpc.TaskOperation.RELOAD);
    }

    @Override
    public Mono<Void> start() {
        return operation(TaskRpc.TaskOperation.START);
    }

    @Override
    public Mono<Void> pause() {
        return operation(TaskRpc.TaskOperation.PAUSE);
    }

    @Override
    public Mono<Void> shutdown() {
        return operation(TaskRpc.TaskOperation.SHUTDOWN);
    }

    @Override
    public Mono<Void> execute(RuleData data) {
        return rpcService.executeTask(id, data).then();
    }

    @Override
    public Mono<State> getState() {
        return rpcService
                .getTaskState(id);
    }

    @Override
    public Mono<Void> debug(boolean debug) {
        return operation(debug ? TaskRpc.TaskOperation.ENABLE_DEBUG : TaskRpc.TaskOperation.DISABLE_DEBUG);
    }

    @Override
    public Mono<Long> getLastStateTime() {
        return rpcService.getLastStateTime(id);
    }

    @Override
    public Mono<Long> getStartTime() {
        return rpcService.getStartTime(id);
    }
}
