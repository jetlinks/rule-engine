package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
import org.jetlinks.core.monitor.observation.Observations;
import org.jetlinks.core.monitor.recorder.ActionRecorder;
import org.jetlinks.core.monitor.recorder.Recorder;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskExecutor;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * 按输入执行函数节点，统一直接调用和订阅输入的执行、可选观测与上下文准备。
 * 扩展在每次订阅内运行，不缓存输入身份或持有活动 Recorder；输出与错误沿原任务通道传递。
 *
 * @since 1.3.3
 * @see ExecutionContext
 */
public abstract class FunctionTaskExecutor extends AbstractTaskExecutor implements TaskExecutor {

    @Getter
    private final String name;

    public FunctionTaskExecutor(String name, ExecutionContext context) {
        super(context);
        this.name = name;
    }

    protected abstract Publisher<RuleData> apply(RuleData input);

    /**
     * 单条输入的异步处理边界，直接 execute 与订阅输入共用。
     * 子类可适配输入/输出载体，通用观测由执行器负责；不应新增订阅或改变错误处理。
     * 框架在每次订阅内调用，默认直接适配 apply 的返回流，无需再次 defer。
     *
     * @param input 当前规则输入
     * @return 原业务输出流
     * @since 1.3.3
     */
    protected Flux<RuleData> applyAsync(RuleData input) {
        return Flux.from(this.apply(input));
    }

    /**
     * 是否由公共执行边界记录一次动作。旧手工埋点节点默认关闭，迁移后显式开启；
     * Monitor 为 noop 时只执行业务，不创建动作记录。
     *
     * @return 是否启用通用观测
     * @since 1.3.3
     */
    protected boolean observeExecution() {
        return false;
    }

    /**
     * 为本次未启动动作追加业务标签，不负责 start/complete/error。
     *
     * @param recorder 本次原生动作句柄
     * @param input 当前输入
     * @since 1.3.3
     */
    protected void tagObservation(ActionRecorder<?> recorder, RuleData input) {
        recorder.tag(RuleConstants.Tags.contextId, input.getContextId());
    }

    /**
     * 在动作创建前恢复输入携带的订阅上下文。默认保持调用方上下文；
     * 载体适配由上层实现，不能在这里启动订阅或保存请求状态。
     *
     * @param context 当前订阅上下文
     * @param input 本次输入
     * @return 恢复后的上下文
     * @since 1.3.3
     */
    protected Context prepareContext(Context context, RuleData input) {
        return context;
    }

    private Flux<RuleData> executeInput(RuleData input) {
        return Flux.defer(() -> {
            if (!observeExecution()) {
                return applyAsync(input).contextWrite(ctx -> prepareContext(ctx, input));
            }
            Recorder recorder = observationRecorder(input);
            return Observations.observeFlux(recorder, "execute", () -> applyAsync(input))
                .contextWrite(ctx -> prepareContext(ctx, input).put(Recorder.class, recorder));
        });
    }

    private Recorder observationRecorder(RuleData input) {
        Recorder original = context.monitor().recorder();
        if (original == Recorder.noop()) {
            return original;
        }
        return new Recorder() {
            @Override
            public <E> ActionRecorder<E> action(CharSequence action) {
                ActionRecorder<E> recorder = original.action(action);
                tagObservation(recorder, input);
                return recorder;
            }
        };
    }

    private Mono<Void> doApply(RuleData input) {

        return executeInput(input)
            .concatMap(data -> context
                .fireEvent(RuleConstants.Event.result, data)
                .then(context.getOutput().write(data)), 0)
            .then(context.fireEvent(RuleConstants.Event.complete, input))
            .contextWrite(contextWriter())
            .as(tracer())
            .onErrorResume(error -> {
                context.logger().warn(error.getLocalizedMessage(),error);
                return context.onError(error, input);
            })
            .then();
    }

    @Override
    public final Mono<Void> execute(RuleData ruleData) {
        return doApply(context.newRuleData(ruleData));
    }

    @Override
    protected Disposable doStart() {
        return context
            .getInput()
            .accept(data -> {
                if (getState() != Task.State.running) {
                    return Mono.empty();
                }
                return this
                    .doApply(data)
                    .then(Reactors.ALWAYS_TRUE);
            })
            ;
    }


}
