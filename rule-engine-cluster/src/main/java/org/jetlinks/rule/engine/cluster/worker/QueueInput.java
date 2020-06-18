package org.jetlinks.rule.engine.cluster.worker;

import lombok.AllArgsConstructor;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.cluster.ClusterQueue;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.Input;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.List;

@AllArgsConstructor
public class QueueInput implements Input {

    private final String instanceId;

    private final String nodeId;

    //输入节点
    private final List<ScheduleJob.Event> events;

    private final ClusterManager clusterManager;

    @Override
    public Flux<RuleData> accept() {
        ClusterQueue<RuleData> input = clusterManager.getQueue(RuleConstants.Topics.input(instanceId, nodeId));

        if (!CollectionUtils.isEmpty(events)) {
            return Flux.fromIterable(events)
                    .map(event -> clusterManager.<RuleData>getQueue(RuleConstants.Topics.input(instanceId, nodeId)).subscribe())
                    .as(Flux::merge)
                    .mergeWith(input.subscribe());
        }

        return input.subscribe();
    }

}
