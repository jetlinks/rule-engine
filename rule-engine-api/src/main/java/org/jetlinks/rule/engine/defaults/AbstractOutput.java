package org.jetlinks.rule.engine.defaults;

import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.core.trace.TraceHolder;
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

public abstract class AbstractOutput implements Output {

    private final String instanceId;

    private final List<ScheduleJob.Output> outputs;

    private final ConditionEvaluator evaluator;

    private Function<RuleData, Mono<Boolean>> writer;

    public AbstractOutput(String instanceId,
                          List<ScheduleJob.Output> outputs,
                          ConditionEvaluator evaluator) {
        this.instanceId = instanceId;
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
                String address = createOutputAddress(output.getOutput());
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

            Flux<Function<RuleData, Mono<Boolean>>> flux = Flux.fromIterable(writers);

            this.writer = data -> TraceHolder
                    .writeContextTo(data, RuleData::setHeader)
                    .flatMap(ruleData -> flux
                            .flatMap(writer -> writer
                                    .apply(ruleData)
                                    .onErrorResume(err -> Reactors.ALWAYS_FALSE))
                            .reduce((a, b) -> a && b));
        }
    }

    @Override
    public final Mono<Boolean> write(RuleData data) {
        return writer.apply(data);
    }

    @Override
    public final Mono<Boolean> write(Publisher<RuleData> dataStream) {
        return Flux
                .from(dataStream)
                .flatMap(this::write)
                .all(Boolean::booleanValue)
                ;
    }

    @Override
    public final Mono<Void> write(String nodeId, Publisher<RuleData> data) {
        return doWrite(createOutputAddress(nodeId), data)
                .then();
    }

    protected abstract Mono<Boolean> doWrite(String address, Publisher<RuleData> data);

    protected abstract Mono<Boolean> doWrite(String address, RuleData data);

    @Override
    public final Mono<Void> write(String nodeId, RuleData data) {
        return doWrite(createOutputAddress(nodeId), data)
                .then();
    }

    protected String createOutputAddress(String nodeId) {
        return RuleConstants.Topics.input(instanceId, nodeId);
    }
}
