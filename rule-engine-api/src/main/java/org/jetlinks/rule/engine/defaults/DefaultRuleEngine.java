package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.Scheduler;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.executor.ScheduleJob;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;


@AllArgsConstructor
public class DefaultRuleEngine {

    //调度器
    private final Scheduler scheduler;

    public Flux<Task> startRule(String instanceId,
                                RuleModel model) {
        return Flux.fromIterable(model.getNodes())
                .map(node -> createScheduleJob(instanceId, model, node))
                .flatMap(scheduler::schedule)
                .flatMap(task -> task.start().thenReturn(task));
    }

    public Flux<Task> getTasks(String instanceId){
        return scheduler.getSchedulingJob(instanceId);
    }

    protected ScheduleJob createScheduleJob(String instanceId,
                                            RuleModel model,
                                            RuleNodeModel node) {
        ScheduleJob job = new ScheduleJob();
        job.setInstanceId(instanceId);
        job.setRuleId(model.getId());
        job.setNodeId(node.getId());
        job.setConfiguration(node.getConfiguration());
        job.setExecutor(node.getExecutor());
        job.setName(node.getName());
        job.setSchedulingRule(node.getSchedulingRule());

        {
            List<String> inputs = new ArrayList<>();

            for (RuleLink input : node.getInputs()) {
                inputs.add(input.getSource().getId());
            }
            job.setInputs(inputs);
        }

        {
            List<ScheduleJob.Output> outputs = new ArrayList<>();
            for (RuleLink output : node.getOutputs()) {
                outputs.add(new ScheduleJob.Output(output.getTarget().getId(), output.getCondition()));
            }
            job.setOutputs(outputs);
        }

        {
            List<ScheduleJob.Event> events = new ArrayList<>();
            for (RuleLink event : node.getEvents()) {
                events.add(new ScheduleJob.Event(event.getType(), event.getTarget().getId()));
            }
            job.setEvents(events);
        }

        return job;
    }

}
