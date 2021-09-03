package org.jetlinks.rule.engine.cluster.worker;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;
import org.jetlinks.rule.engine.api.task.Output;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@AllArgsConstructor
public class QueueOutput implements Output {

    private final String instanceId;

    private final ClusterManager clusterManager;

    private final List<ScheduleJob.Output> outputs;

    private final ConditionEvaluator evaluator;

    @Override
    public Mono<Boolean> write(Publisher<RuleData> dataStream) {
        return Flux
                .from(dataStream)
                .flatMap(data -> Flux
                        .fromIterable(outputs)
                        .filterWhen(output -> Mono
                                .fromCallable(() -> evaluator.evaluate(output.getCondition(), data))
                                .onErrorResume(error -> {
                                    log.warn(error.getMessage(), error);
                                    return Mono.just(false);
                                }))
                        .flatMap(out -> clusterManager
                                         .getQueue(createTopic(out.getOutput()))
                                         .add(Mono.just(data)),
                                 Integer.MAX_VALUE)
                )
                .then(Mono.just(true))
                ;
    }

    @Override
    public Mono<Void> write(String nodeId, Publisher<RuleData> data) {
        return clusterManager
                .<RuleData>getQueue(createTopic(nodeId))
                .add(data)
                .then();
    }

    private String createTopic(String node) {
        return RuleConstants.Topics.input(instanceId, node);
    }

}
