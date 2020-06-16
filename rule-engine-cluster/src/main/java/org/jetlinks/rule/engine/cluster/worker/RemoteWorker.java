package org.jetlinks.rule.engine.cluster.worker;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.cluster.scheduler.SchedulerRpcService;
import org.jetlinks.rule.engine.cluster.task.RemoteTask;
import reactor.core.publisher.Mono;

import java.util.List;

@AllArgsConstructor
public class RemoteWorker implements Worker {

    @Getter
    private final String id;

    @Getter
    private final String name;

    private final SchedulerRpcService rpcService;

    @Override
    public Mono<Task> createTask(String schedulerId, ScheduleJob job) {
        return rpcService
                .createTask(id,job)
                .map(response -> new RemoteTask(
                        response.getId(),
                        response.getName(),
                        id,
                        schedulerId,
                        rpcService,
                        job
                ));
    }

    @Override
    public Mono<List<String>> getSupportExecutors() {
        return rpcService
                .getSupportExecutors(id);
    }

    @Override
    public Mono<State> getState() {
        return rpcService
                .getWorkerState(id);
    }
}
