package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.Scheduler;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.Worker;
import org.jetlinks.rule.engine.api.ScheduleJob;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.cluster.task.RemoteTask;
import org.jetlinks.rule.engine.cluster.worker.RemoteWorker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class RemoteScheduler implements Scheduler {

    @Getter
    private final String id;

    @Getter
    private final RpcService rpcService;

    @Override
    public Flux<Worker> getWorkers() {
        return rpcService
                .invoke(SchedulerRpc.getWorkers(id))
                .map(info -> new RemoteWorker(info.getId(), info.getName(), rpcService))
                ;
    }

    @Override
    public Mono<Worker> getWorker(String workerId) {
        return getWorkers()
                .filter(worker -> worker.getId().equals(workerId))
                .singleOrEmpty();
    }

    @Override
    public Flux<Task> schedule(ScheduleJob job) {
        return rpcService
                .invoke(SchedulerRpc.schedule(id), job)
                .map(taskInfo -> new RemoteTask(taskInfo.getId(), taskInfo.getName(), taskInfo.getWorkerId(), rpcService, job));
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        return rpcService
                .invoke(SchedulerRpc.shutdown(id), instanceId)
                .then();
    }

    @Override
    public Flux<Task> getSchedulingJob(String instanceId) {
        return rpcService
                .invoke(SchedulerRpc.getSchedulingJobs(id), instanceId)
                .map(taskInfo -> new RemoteTask(taskInfo.getId(), taskInfo.getName(), taskInfo.getWorkerId(), rpcService, taskInfo.getJob()));
    }

    @Override
    public Flux<Task> getSchedulingJobs() {
        return rpcService
                .invoke(SchedulerRpc.getSchedulingAllJobs(id))
                .map(taskInfo -> new RemoteTask(taskInfo.getId(), taskInfo.getName(), taskInfo.getWorkerId(), rpcService, taskInfo.getJob()));
    }

    @Override
    public Mono<Long> totalTask() {
        return rpcService
                .invoke(SchedulerRpc.getTotalTasks(id))
                .single(0L);
    }

    @Override
    public Mono<Boolean> canSchedule(ScheduleJob job) {
        return rpcService
                .invoke(SchedulerRpc.canSchedule(id), job)
                .single(false);
    }
}
