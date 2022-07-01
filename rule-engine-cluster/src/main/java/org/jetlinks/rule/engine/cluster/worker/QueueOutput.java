package org.jetlinks.rule.engine.cluster.worker;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;
import org.jetlinks.rule.engine.api.task.Output;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Slf4j
public class QueueOutput implements Output {

    private final String instanceId;

    private final ClusterManager clusterManager;

    private final List<ScheduleJob.Output> outputs;

    private final ConditionEvaluator evaluator;

    private Function<RuleData, Mono<Boolean>> writer;

    public QueueOutput(String instanceId,
                       ClusterManager clusterManager,
                       List<ScheduleJob.Output> outputs,
                       ConditionEvaluator evaluator) {
        this.instanceId = instanceId;
        this.clusterManager = clusterManager;
        this.outputs = outputs;
        this.evaluator = evaluator;
        prepare();
    }

    //预处理规则数据输出逻辑
    private void prepare() {

        //没有输出
        if (CollectionUtils.isEmpty(outputs)) {
            writer = data -> Reactors.ALWAYS_TRUE;
        } else {
            List<Function<RuleData, Mono<Boolean>>> writers = new ArrayList<>(outputs.size());

            for (ScheduleJob.Output output : outputs) {
                String queueId = createQueueName(output.getOutput());
                Function<RuleData, Mono<Boolean>> writer;
                //配置了输出条件
                if (output.getCondition() != null) {
                    Function<RuleData, Mono<Boolean>> condition = evaluator.prepare(output.getCondition());
                    writer = data -> condition
                            .apply(data)
                            .flatMap(passed -> {
                                //条件判断返回true才认为成功
                                if (passed) {
                                    return clusterManager
                                            .getQueue(queueId)
                                            .add(data);
                                }
                                return Reactors.ALWAYS_FALSE;
                            });
                } else {
                    writer = (data) -> clusterManager.getQueue(queueId).add(data);
                }
                writers.add(writer);
            }

            Flux<Function<RuleData, Mono<Boolean>>> flux = Flux.fromIterable(writers);

            this.writer = data -> flux
                    .flatMap(writer -> writer
                            .apply(data)
                            .onErrorResume(err -> Reactors.ALWAYS_FALSE))
                    .reduce((a, b) -> a && b);
        }
    }

    @Override
    public Mono<Boolean> write(RuleData data) {
        return writer.apply(data);
    }

    @Override
    public Mono<Boolean> write(Publisher<RuleData> dataStream) {
        return Flux
                .from(dataStream)
                .flatMap(this::write)
                .all(Boolean::booleanValue)
                ;
    }

    @Override
    public Mono<Void> write(String nodeId, Publisher<RuleData> data) {
        return clusterManager
                .<RuleData>getQueue(createQueueName(nodeId))
                .add(data)
                .then();
    }

    @Override
    public Mono<Void> write(String nodeId, RuleData data) {
        return clusterManager
                .<RuleData>getQueue(createQueueName(nodeId))
                .add(data)
                .then();
    }

    private String createQueueName(String nodeId) {
        return RuleConstants.Topics.input(instanceId, nodeId);
    }

}
