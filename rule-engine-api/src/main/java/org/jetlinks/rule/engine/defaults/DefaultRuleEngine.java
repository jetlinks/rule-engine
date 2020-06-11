package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.Scheduler;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.model.RuleModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;


@AllArgsConstructor
public class DefaultRuleEngine {

    //调度器
    private final Scheduler scheduler;

    public Flux<Task> startRule(String instanceId,
                                RuleModel model) {
        return Flux.fromIterable(new ScheduleJobCompiler(instanceId, model).compile())
                .flatMap(scheduler::schedule)
                .collectList()
                .flatMapIterable(Function.identity())
                .flatMap(task -> task.start().thenReturn(task));
    }

    public Flux<Task> getTasks(String instanceId) {
        return scheduler.getSchedulingJob(instanceId);
    }

    public Mono<Void> shutdown(String instanceId) {
        return scheduler.shutdown(instanceId);
    }


}
