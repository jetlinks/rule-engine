package org.jetlinks.rule.engine.cluster.worker;

import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.Worker;
import org.jetlinks.rule.engine.api.ScheduleJob;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.cluster.task.ClusterLocalTask;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClusterLocalWorker implements Worker {

    private final Worker localWorker;

    private final RpcService rpcService;

    public List<Disposable> disposables = new CopyOnWriteArrayList<>();

    public ClusterLocalWorker(Worker localWorker, RpcService rpcService) {
        this.localWorker = localWorker;
        this.rpcService = rpcService;
    }

    public void setup() {
        disposables.add(
                rpcService.listen(
                        WorkerRpc.createTask(getId()),
                        (addr, job) -> createTask(job.getSchedulerId(), job.getJob())
                                .map(task -> new WorkerRpc.CreateTaskResponse(task.getId(), task.getName())))
        );

        disposables.add(rpcService.listen(
                WorkerRpc.getSupportExecutors(getId()),
                (addr) -> getSupportExecutors()));

        disposables.add(rpcService.listen(
                WorkerRpc.getWorkerState(getId()),
                (addr) -> getState()));

    }

    public void cleanup() {
        disposables.forEach(Disposable::dispose);
        disposables.clear();
    }


    @Override
    public String getId() {
        return localWorker.getId();
    }

    @Override
    public String getName() {
        return localWorker.getName();
    }

    @Override
    public Mono<Task> createTask(String schedulerId, ScheduleJob job) {

        return localWorker.createTask(schedulerId, job)
                .map(task->{
                    ClusterLocalTask clusterLocalTask= new ClusterLocalTask(task,rpcService);
                    clusterLocalTask.setup();
                    return clusterLocalTask;
                })
                ;
    }

    @Override
    public Mono<List<String>> getSupportExecutors() {
        return localWorker.getSupportExecutors();
    }

    @Override
    public Mono<State> getState() {
        return localWorker.getState();
    }
}
