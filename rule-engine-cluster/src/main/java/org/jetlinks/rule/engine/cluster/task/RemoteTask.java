package org.jetlinks.rule.engine.cluster.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.ScheduleJob;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.reactivestreams.Publisher;
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
    private final RpcService rpcService;

    @Getter
    private ScheduleJob job;

    @Override
    public Mono<Void> setJob(ScheduleJob job) {
        this.job = job;
        return rpcService
                .invoke(TaskRpc.setTaskJob(workerId, id), job)
                .then();
    }

    private Mono<Void> operation(TaskRpc.TaskOperation operation) {
        return rpcService
                .invoke(TaskRpc.taskOperation(workerId, id), operation)
                .then();
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
    public Mono<Void> execute(Publisher<RuleData> data) {
        return rpcService.invoke(TaskRpc.executeTask(workerId, id), data).then();
    }

    @Override
    public Mono<State> getState() {
        return rpcService
                .invoke(TaskRpc.getTaskState(workerId, id))
                .single(State.unknown);
    }

    @Override
    public Mono<Void> debug(boolean debug) {
        return operation(debug ? TaskRpc.TaskOperation.ENABLE_DEBUG : TaskRpc.TaskOperation.DISABLE_DEBUG);
    }

    @Override
    public Mono<Long> getLastStateTime() {
        return rpcService
                .invoke(TaskRpc.getLastStateTime(workerId, id))
                .singleOrEmpty()
                ;
    }

    @Override
    public Mono<Long> getStartTime() {
        return rpcService
                .invoke(TaskRpc.getStartTime(workerId, id))
                .singleOrEmpty()
                ;
    }
}
