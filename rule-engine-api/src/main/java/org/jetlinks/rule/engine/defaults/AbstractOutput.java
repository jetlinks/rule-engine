package org.jetlinks.rule.engine.defaults;

import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.core.trace.TraceHolder;
import org.jetlinks.core.monitor.metrics.RateMeter;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ConditionEvaluator;
import org.jetlinks.rule.engine.api.task.Output;
import org.jetlinks.rule.engine.api.task.OutputMetrics;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public abstract class AbstractOutput extends RateMeter implements Output {

    protected final String instanceId;

    // null 表示无下游；直接保存路由，不再用聚合函数包装一次。
    private final Flux<Function<RuleData, Mono<Boolean>>> writers;

    public AbstractOutput(String instanceId,
                          List<ScheduleJob.Output> outputs,
                          ConditionEvaluator evaluator) {
        this.instanceId = instanceId;
        this.writers = prepare(outputs, evaluator);
    }

    //预处理规则数据输出逻辑
    private Flux<Function<RuleData, Mono<Boolean>>> prepare(List<ScheduleJob.Output> outputs, ConditionEvaluator evaluator) {
        if (!CollectionUtils.isEmpty(outputs)) {
            List<Function<RuleData, Mono<Boolean>>> writers = new ArrayList<>(outputs.size());

            for (ScheduleJob.Output output : outputs) {
                CharSequence address = createOutputAddress(output.getOutput());
                Function<RuleData, Mono<Boolean>> writer;
                //配置了输出条件
                if (output.getCondition() != null) {
                    Function<RuleData, Mono<Boolean>> condition = evaluator.prepare(output.getCondition());
                    writer = data -> condition
                            .apply(data)
                            .flatMap(passed -> {
                                //条件判断返回true才认为成功
                                if (passed) {
                                    return doWrite(address, data);
                                }
                                return Reactors.ALWAYS_FALSE;
                            });
                } else {
                    writer = (data) -> doWrite(address, data);
                }
                writers.add(writer);
            }

            return Flux.fromIterable(writers);
        }
        return null;
    }

    @Override
    public final Mono<Boolean> write(RuleData data) {
        if (writers == null) {
            mark();
            return Reactors.ALWAYS_TRUE;
        }
        return TraceHolder
            .writeContextTo(data, RuleData::setHeader)
            .flatMap(ruleData -> {
                mark();
                return writers.flatMap(writer -> writer.apply(ruleData)
                    .onErrorResume(err -> Reactors.ALWAYS_FALSE))
                    .reduce(Boolean::logicalAnd);
            });
    }

    @Override
    public final Mono<Boolean> write(Publisher<RuleData> dataStream) {
        return Flux
                .from(dataStream)
                .flatMap(this::write)
                .reduce(Boolean::logicalAnd);
    }

    @Override
    public final Mono<Void> write(String nodeId, Publisher<RuleData> data) {
        return doWrite(createOutputAddress(nodeId), Flux.from(data).doOnNext(ignored -> mark()))
                .then();
    }

    protected abstract Mono<Boolean> doWrite(CharSequence address, Publisher<RuleData> data);

    protected abstract Mono<Boolean> doWrite(CharSequence address, RuleData data);

    @Override
    public final Mono<Void> write(String nodeId, RuleData data) {
        return Mono.defer(() -> {
            mark();
            return doWrite(createOutputAddress(nodeId), data).then();
        });
    }

    @Override
    public final OutputMetrics getMetrics() {
        return new OutputMetrics(getCount(), getOneMinuteRate());
    }

    protected CharSequence createOutputAddress(String nodeId) {
        return RuleConstants.Topics.input(instanceId, nodeId);
    }
}
