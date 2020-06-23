package org.jetlinks.rule.engine.cluster.worker;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.Output;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class QueueEventOutput implements Output {
    private final String instanceId;

    private final ClusterManager clusterManager;

    @Getter
    private final String event;

    private final String sourceNode;

    @Override
    public Mono<Boolean> write(Publisher<RuleData> dataStream) {
        return clusterManager.<RuleData>getQueue(createTopic(sourceNode)).add(dataStream);
    }

    @Override
    public Mono<Void> write(String nodeId, Publisher<RuleData> data) {
        return clusterManager.<RuleData>getQueue(createTopic(nodeId)).add(data).then();
    }

    private String createTopic(String node) {
        return RuleConstants.Topics.input(instanceId, node);
    }
}
