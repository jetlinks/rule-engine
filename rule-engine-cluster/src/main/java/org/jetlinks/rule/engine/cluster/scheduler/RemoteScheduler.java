package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.rpc.RpcServiceFactory;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.cluster.task.RemoteTask;
import org.jetlinks.rule.engine.cluster.worker.RemoteWorker;
import reactor.bool.BooleanUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

@AllArgsConstructor
public class RemoteScheduler implements Scheduler {

    @Getter
    private final String id;

    private SchedulerRpcService rpcService;

    private final RpcServiceFactory factory;

    public RemoteScheduler(String id, RpcServiceFactory factory) {
        this.id = id;
        this.factory = factory;
    }

    public void init() {
        this.rpcService = factory.createProducer("/rule-engine/cluster-scheduler:" + id, SchedulerRpcService.class);
    }

    public Mono<Boolean> isAlive() {
        return rpcService
                .isAlive()
                .onErrorResume(TimeoutException.class, r -> Mono.just(false));
    }

    public Mono<Boolean> isNoAlive() {
        return BooleanUtils.not(isAlive());
    }

    @Override
    public Flux<Worker> getWorkers() {
        return rpcService
                .getWorkers()
                .map(info -> new RemoteWorker(info.getId(), info.getName(), rpcService));
    }

    @Override
    public Mono<Worker> getWorker(String workerId) {
        return rpcService
                .getWorker(workerId)
                .map(info -> new RemoteWorker(info.getId(), info.getName(), rpcService));
    }

    @Override
    public Flux<Task> schedule(ScheduleJob job) {
        return rpcService
                .schedule(job)
                .map(taskInfo -> new RemoteTask(taskInfo.getId(), taskInfo.getName(), taskInfo.getWorkerId(), id, rpcService, job));
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        return rpcService
                .shutdown(instanceId);
    }

    @Override
    public Flux<Task> getSchedulingTask(String instanceId) {
        return rpcService
                .getSchedulingTask(instanceId)
                .map(taskInfo -> new RemoteTask(taskInfo.getId(), taskInfo.getName(), taskInfo.getWorkerId(), id, rpcService, taskInfo.getJob()));
    }

    @Override
    public Flux<Task> getSchedulingTasks() {
        return rpcService
                .getSchedulingTasks()
                .map(taskInfo -> new RemoteTask(taskInfo.getId(), taskInfo.getName(), taskInfo.getWorkerId(), id, rpcService, taskInfo.getJob()));
    }

    @Override
    public Mono<Long> totalTask() {
        return rpcService
                .totalTask()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Boolean> canSchedule(ScheduleJob job) {
        return rpcService
                .canSchedule(job);
    }
}
