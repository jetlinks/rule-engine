package org.jetlinks.rule.engine.cluster.task;

import lombok.Getter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Getter
public class ClusterLocalTask implements Task {

    private final Task localTask;

    private final RpcService rpcService;

    private final List<Disposable> disposables = new CopyOnWriteArrayList<>();

    private final static Map<TaskRpc.TaskOperation, Function<Task, Mono<Void>>> operationMapping = new HashMap<>();

    static {
        operationMapping.put(TaskRpc.TaskOperation.PAUSE, Task::pause);
        operationMapping.put(TaskRpc.TaskOperation.START, Task::start);
        operationMapping.put(TaskRpc.TaskOperation.SHUTDOWN, Task::shutdown);
        operationMapping.put(TaskRpc.TaskOperation.RELOAD, Task::reload);
        operationMapping.put(TaskRpc.TaskOperation.ENABLE_DEBUG, task -> task.debug(true));
        operationMapping.put(TaskRpc.TaskOperation.DISABLE_DEBUG, task -> task.debug(false));
    }

    public ClusterLocalTask(Task localTask, RpcService rpcService) {
        this.localTask = localTask;
        this.rpcService = rpcService;
    }

    public void setup() {
        if (!disposables.isEmpty()) {
            return;
        }
        disposables.add(rpcService
                .listen(
                        TaskRpc.getTaskState(getWorkerId(), getId()),
                        (address) -> getState()
                ));

        disposables.add(rpcService
                .listen(
                        TaskRpc.taskOperation(getWorkerId(), getId()),
                        (address, operation) -> operationMapping
                                .get(operation)
                                .apply(this)
                                .then(Mono.empty())
                ));

        disposables.add(rpcService
                .listen(TaskRpc.setTaskJob(getWorkerId(), getId()),
                        (address, requests) -> this.setJob(requests))
        );

        disposables.add(rpcService
                .listen(TaskRpc.getStartTime(getWorkerId(), getId()),
                        (address) -> this.getStartTime())
        );

        disposables.add(rpcService
                .listen(TaskRpc.getLastStateTime(getWorkerId(), getId()),
                        (address) -> this.getLastStateTime())
        );

        disposables.add(rpcService
                .listen(TaskRpc.executeTask(getWorkerId(), getId()),
                        (address, ruleData) -> this.execute(Mono.just(ruleData)))
        );

    }

    public void cleanup() {
        disposables.forEach(Disposable::dispose);
        disposables.clear();
    }

    @Override
    public String getId() {
        return localTask.getId();
    }

    @Override
    public String getName() {
        return localTask.getName();
    }

    @Override
    public String getWorkerId() {
        return localTask.getWorkerId();
    }

    @Override
    public String getSchedulerId() {
        return localTask.getSchedulerId();
    }

    @Override
    public ScheduleJob getJob() {
        return localTask.getJob();
    }

    @Override
    public Mono<Void> setJob(ScheduleJob job) {
        return localTask.setJob(job);
    }

    @Override
    public Mono<Void> reload() {
        return localTask.reload();
    }

    @Override
    public Mono<Void> start() {
        return localTask.start();
    }

    @Override
    public Mono<Void> pause() {
        return localTask.pause();
    }

    @Override
    public Mono<Void> shutdown() {
        return localTask.shutdown();
    }

    @Override
    public Mono<Void> execute(Publisher<RuleData> data) {
        return localTask.execute(data);
    }

    @Override
    public Mono<State> getState() {
        return localTask.getState();
    }

    @Override
    public Mono<Void> debug(boolean debug) {
        return localTask.debug(debug);
    }

    @Override
    public Mono<Long> getLastStateTime() {
        return localTask.getLastStateTime();
    }

    @Override
    public Mono<Long> getStartTime() {
        return localTask.getStartTime();
    }
}
