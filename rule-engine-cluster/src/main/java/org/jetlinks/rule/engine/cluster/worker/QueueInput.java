package org.jetlinks.rule.engine.cluster.worker;

import lombok.AllArgsConstructor;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.cluster.ClusterQueue;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.Input;
import reactor.core.publisher.Flux;

@AllArgsConstructor
public class QueueInput implements Input {

    private final String instanceId;

    private final String nodeId;

    private final ClusterManager clusterManager;

    @Override
    public Flux<RuleData> accept() {
        ClusterQueue<RuleData> input = clusterManager.getQueue(RuleConstants.Topics.input(instanceId, nodeId));


        return input.subscribe();
    }

}
