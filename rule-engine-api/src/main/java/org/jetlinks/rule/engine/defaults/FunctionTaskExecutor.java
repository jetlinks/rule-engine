package org.jetlinks.rule.engine.defaults;

import lombok.Getter;
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

    private Mono<Void> doApply(RuleData input) {

        return observe(input, this::applyAsync)
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
