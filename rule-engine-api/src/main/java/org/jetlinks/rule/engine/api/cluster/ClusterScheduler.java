package org.jetlinks.rule.engine.api.cluster;

import lombok.Getter;
import org.jetlinks.rule.engine.api.Scheduler;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.Worker;
import org.jetlinks.rule.engine.api.events.EventBus;
import org.jetlinks.rule.engine.api.executor.ScheduleJob;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;

/**
 * 集群调度器,用于管理集群中所有调度器
 *
 * @author zhouhao
 */
public class ClusterScheduler implements Scheduler {

    @Getter
    private String id;

    private WorkerRegistry workerRegistry;

    private SchedulerRegistry schedulerRegistry;

    private EventBus eventBus;

    private boolean leader;

    private final Map<String/*schedulerId*/, Map<String/*workerId*/, Set<Task>>> executors = new ConcurrentHashMap<>();

    public void start() {

        //加载全部task信息到内存
        Flux.concat(schedulerRegistry.getSchedulers(), schedulerRegistry.handleSchedulerJoin())
                .flatMap(scheduler -> scheduler
                        .getSchedulingJobs()
                        .doOnNext(task -> getTask(scheduler.getId(), task.getWorkerId()).add(task))
                        .then(Mono.just(scheduler))
                )
                .subscribe();


        schedulerRegistry
                .handleSchedulerLeave()
                .doOnNext(scheduler -> {
                    doReBalance(scheduler.getId());
                })
                .subscribe();
    }

    private void leaderElection() {


    }

    private void doReBalance(String schedulerId) {
        if (leader) {

        }
    }

    private Set<Task> getTask(String workerId, String nodeId) {
        return getTask(workerId).computeIfAbsent(nodeId, ignore -> new ConcurrentSkipListSet<>(Comparator.comparing(Task::getId)));
    }

    private Map<String, Set<Task>> getTask(String instanceId) {
        return executors.computeIfAbsent(instanceId, ignore -> new ConcurrentHashMap<>());
    }

    @Override
    public Flux<Worker> getWorkers() {
        return workerRegistry.getWorkers();
    }

    @Override
    public Mono<Worker> getWorker(String workerId) {
        return workerRegistry.getWorkers()
                .filter(worker -> worker.getId().equals(workerId))
                .take(1)
                .singleOrEmpty();
    }

    @Override
    public Flux<Task> schedule(ScheduleJob job) {

        // TODO: 2020/6/11  
        return null;
    }

    @Override
    public Mono<Void> shutdown(String instanceId) {
        // TODO: 2020/6/11
        return null;
    }

    @Override
    public Flux<Task> getSchedulingJob(String instanceId) {
        return getSchedulingJobs()
                .filter(task -> task.getJob().getInstanceId().equals(instanceId))
                ;
    }

    @Override
    public Flux<Task> getSchedulingJobs() {
        return Flux.fromIterable(executors.values())
                .flatMapIterable(Map::values)
                .flatMapIterable(Function.identity());
    }
}
