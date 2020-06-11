package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import org.jetlinks.rule.engine.api.ConditionEvaluator;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.TaskExecutorProvider;
import org.jetlinks.rule.engine.api.Worker;
import org.jetlinks.rule.engine.api.events.EventBus;
import org.jetlinks.rule.engine.api.executor.ScheduleJob;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalWorker implements Worker {

    private final Map<String, TaskExecutorProvider> executors = new ConcurrentHashMap<>();

    @Getter
    private final String id;

    @Getter
    private final String name;

    @Getter
    private final String host;

    private final EventBus eventBus;

    private final ConditionEvaluator conditionEvaluator;

    public LocalWorker(String id, String name, String host, EventBus eventBus, ConditionEvaluator evaluator) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.eventBus = eventBus;
        this.conditionEvaluator = evaluator;
    }

    @Override
    public Mono<Task> createTask(ScheduleJob job) {
        return Mono.justOrEmpty(executors.get(job.getExecutor()))
                .switchIfEmpty(Mono.error(() -> new UnsupportedOperationException("unsupported executor:" + job.getExecutor())))
                .flatMap(provider -> {
                    DefaultExecutionContext context = createContext(job);
                    return provider.createTask(context)
                            .map(executor -> new DefaultTask(this.getId(), context, executor));
                });
    }

    protected DefaultExecutionContext createContext(ScheduleJob job) {
        return new DefaultExecutionContext(job, eventBus, conditionEvaluator);
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
