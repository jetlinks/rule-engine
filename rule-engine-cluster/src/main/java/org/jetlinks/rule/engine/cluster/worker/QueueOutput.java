package org.jetlinks.rule.engine.cluster.worker;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;
import org.jetlinks.rule.engine.defaults.AbstractOutput;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
public class QueueOutput extends AbstractOutput {

    private final ClusterManager clusterManager;

    public QueueOutput(String instanceId,
                       ClusterManager clusterManager,
                       List<ScheduleJob.Output> outputs,
                       ConditionEvaluator evaluator) {
        super(instanceId, outputs, evaluator);
        this.clusterManager = clusterManager;
    }

    @Override
    protected Mono<Boolean> doWrite(CharSequence address, Publisher<RuleData> data) {
        return clusterManager
            .<RuleData>getQueue(address.toString())
            .add(data);
    }

    @Override
    protected Mono<Boolean> doWrite(CharSequence address, RuleData data) {
        return clusterManager
            .<RuleData>getQueue(address.toString())
            .add(data);
    }
}
