package org.jetlinks.rule.engine.cluster.worker;

import com.alibaba.fastjson.JSON;
import lombok.Getter;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.rule.engine.api.RuleConstants;
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

    private final EventBus eventBus;

    private final RuleIOManager ruleIOManager;

    private final RuleMonitorManager monitorManager;

    @Deprecated
    public ClusterWorker(String id,
                         String name,
                         EventBus eventBus,
                         ClusterManager clusterManager,
                         ConditionEvaluator evaluator) {
        this(id, name, eventBus, new ClusterRuleIOManager(clusterManager, evaluator));
    }

    public ClusterWorker(String id,
                         String name,
                         EventBus eventBus,
                         RuleIOManager ioManager) {
        this(id, name, eventBus, ioManager, new DefaultRuleMonitorManager(id, eventBus));
    }

    public ClusterWorker(String id,
                         String name,
                         EventBus eventBus,
                         RuleIOManager ioManager,
                         RuleMonitorManager monitorManager) {
        this.id = id;
        this.name = name;
        this.eventBus = eventBus;
        this.ruleIOManager = ioManager;
        this.monitorManager = monitorManager;
    }

    @Override
    public Mono<Task> createTask(String schedulerId, ScheduleJob job) {
        return Mono
            .justOrEmpty(executors.get(job.getExecutor()))
            .switchIfEmpty(Mono.error(() -> new UnsupportedOperationException("unsupported executor:" + job.getExecutor())))
            .<Task>flatMap(provider -> {
                ClusterExecutionContext context = createContext(job);
                return provider
                    .createTask(context)
                    .map(executor -> new DefaultTask(schedulerId, this.getId(), context, executor));
            })
            .as(RuleConstants.Trace.traceMono(
                job,
                "create",
                (_job, builder) -> {
                    builder.setAttribute(RuleConstants.Trace.executor, _job.getExecutor());
                    builder.setAttributeLazy(RuleConstants.Trace.configuration, () -> JSON.toJSONString(_job.getConfiguration()));
                }));
    }

    protected ClusterExecutionContext createContext(ScheduleJob job) {
        return new ClusterExecutionContext(job, eventBus, ruleIOManager, monitorManager);
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
