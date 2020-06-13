package org.jetlinks.rule.engine.cluster.worker;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.Worker;
import org.jetlinks.rule.engine.api.ScheduleJob;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.cluster.task.RemoteTask;
import reactor.core.publisher.Mono;

import java.util.List;

@AllArgsConstructor
public class RemoteWorker implements Worker {

    @Getter
    private final String id;

    @Getter
    private final String name;

    private final RpcService rpcService;

    @Override
    public Mono<Task> createTask(String schedulerId, ScheduleJob job) {
        return rpcService
                .invoke(WorkerRpc.createTask(id), new WorkerRpc.CreateTaskRequest(schedulerId, job))
                .singleOrEmpty()
                .map(response -> new RemoteTask(
                        response.getTaskId(),
                        id,
                        response.getTaskName(),
                        schedulerId,
                        rpcService,
                        job
                ));
    }

    @Override
    public Mono<List<String>> getSupportExecutors() {
        return rpcService
                .invoke(WorkerRpc.getSupportExecutors(id))
                .singleOrEmpty();
    }

    @Override
    public Mono<State> getState() {
        return rpcService
                .invoke(WorkerRpc.getWorkerState(id))
                .singleOrEmpty();
    }
}
