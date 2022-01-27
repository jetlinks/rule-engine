package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.core.rpc.DisposableService;
import org.jetlinks.core.rpc.RpcServiceFactory;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.cluster.task.RemoteTask;
import org.jetlinks.rule.engine.cluster.worker.RemoteWorker;
import reactor.bool.BooleanUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

@AllArgsConstructor
public class RemoteScheduler implements Scheduler, Disposable {

    //调度器ID
    @Getter
    private final String id;

    //RPC调度器服务
    private SchedulerRpcService rpcService;

    //远程服务工厂
    private final RpcServiceFactory factory;

    private Disposable disposable;

    public RemoteScheduler(String id, RpcServiceFactory factory) {
        this.id = id;
        this.factory = factory;
    }

    public void init() {
        DisposableService<SchedulerRpcService> service = factory.createProducer("/rule-engine/cluster-scheduler:" + id, SchedulerRpcService.class);
        this.disposable = service;
        this.rpcService = service.getService();
    }

    /**
     * 判断RPC调度器是否存活
     *
     * @return 判断结果
     */
    public Mono<Boolean> isAlive() {
        return rpcService
                .isAlive()
                .onErrorResume(TimeoutException.class, r -> Mono.just(false));
    }

    /**
     * @return 调度器是否已失活
     */
    public Mono<Boolean> isNoAlive() {
        return BooleanUtils.not(isAlive());
    }

    /**
     * @return 全部工作器
     */
    @Override
    public Flux<Worker> getWorkers() {
        return rpcService
                .getWorkers()
                .map(info -> new RemoteWorker(info.getId(), info.getName(), rpcService));
    }

    /**
     * 获取指定ID的工作器
     *
     * @param workerId ID
     * @return 工作器
     */
    @Override
    public Mono<Worker> getWorker(String workerId) {
        return rpcService
                .getWorker(workerId)
                .map(info -> new RemoteWorker(info.getId(), info.getName(), rpcService));
    }

    /**
     * 调度任务并返回执行此任务的执行器,此方法是幂等的,多次调度相同配置的信息,不会创建多个任务。
     *
     * @param job 任务配置
     * @return 返回执行此任务的执行器
     * @see Worker#createTask(String, ScheduleJob)
     */
    @Override
    public Flux<Task> schedule(ScheduleJob job) {
        return rpcService
                .schedule(job)
                .map(taskInfo -> new RemoteTask(taskInfo.getId(), taskInfo.getName(), taskInfo.getWorkerId(), id, rpcService, job));
    }

    /**
     * 停止任务
     * @param instanceId 实例ID
     * @return void Mono
     */
    @Override
    public Mono<Void> shutdown(String instanceId) {
        return rpcService
                .shutdown(instanceId);
    }

    /**
     * 根据规则ID获取全部调度中的任务
     *
     * @param instanceId 规则ID
     * @return 任务执行信息
     */
    @Override
    public Flux<Task> getSchedulingTask(String instanceId) {
        return rpcService
                .getSchedulingTask(instanceId)
                .map(taskInfo -> new RemoteTask(taskInfo.getId(), taskInfo.getName(), taskInfo.getWorkerId(), id, rpcService, taskInfo.getJob()));
    }

    /**
     * 获取全部调度中的任务
     * @return 任务执行信息
     */
    @Override
    public Flux<Task> getSchedulingTasks() {
        return rpcService
                .getSchedulingTasks()
                .map(taskInfo -> new RemoteTask(taskInfo.getId(), taskInfo.getName(), taskInfo.getWorkerId(), id, rpcService, taskInfo.getJob()));
    }

    /**
     * @return 调度中任务总数
     */
    @Override
    public Mono<Long> totalTask() {
        return rpcService
                .totalTask()
                .defaultIfEmpty(0L);
    }

    /**
     * 当前调度器是否可以调度此任务
     *
     * @param job 任务信息
     * @return 是否可以调度
     */
    @Override
    public Mono<Boolean> canSchedule(ScheduleJob job) {
        return rpcService
                .canSchedule(job);
    }

    @Override
    public void dispose() {
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposable == null || disposable.isDisposed();
    }
}
