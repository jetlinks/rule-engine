package org.jetlinks.rule.engine.cluster.worker;

import lombok.Getter;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskExecutorProvider;
import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.defaults.DefaultTask;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClusterWorker implements Worker {

    private final Map<String, TaskExecutorProvider> executors = new ConcurrentHashMap<>();

    @Getter
    private final String id;

    @Getter
    private final String name;

    private final ClusterManager clusterManager;

    private final EventBus eventBus;

    private final ConditionEvaluator conditionEvaluator;

    public ClusterWorker(String id, String name, EventBus eventBus, ClusterManager clusterManager, ConditionEvaluator evaluator) {
        this.id = id;
        this.name = name;
        this.eventBus = eventBus;
        this.conditionEvaluator = evaluator;
        this.clusterManager = clusterManager;
    }

    @Override
    public Mono<Task> createTask(String schedulerId, ScheduleJob job) {
        return Mono
                .justOrEmpty(executors.get(job.getExecutor()))
                .switchIfEmpty(Mono.error(() -> new UnsupportedOperationException("unsupported executor:" + job.getExecutor())))
                .flatMap(provider -> {
                    ClusterExecutionContext context = createContext(job);
                    return provider
                            .createTask(context)
                            .map(executor -> new DefaultTask(schedulerId, this.getId(), context, executor));
                });
    }

    protected ClusterExecutionContext createContext(ScheduleJob job) {
        return new ClusterExecutionContext(getId(), job, eventBus, clusterManager, conditionEvaluator);
    }

    @Override
    public Mono<List<String>> getSupportExecutors() {

        return Mono.just(new ArrayList<>(executors.keySet()));
    }

    @Override
    public Mono<State> getState() {
        return Mono.just(State.working);
    }

    public void addExecutor(TaskExecutorProvider provider) {
        executors.put(provider.getExecutor(), provider);
    }
}
