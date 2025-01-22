package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scope.ContextScope;
import org.jetlinks.rule.engine.api.scope.FlowScope;
import org.jetlinks.rule.engine.api.scope.GlobalScope;
import org.jetlinks.rule.engine.api.scope.NodeScope;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 规则执行上下文,一个上下文对应一个执行中的规则
 *
 * @author zhouhao
 * @since 1.1
 */
public interface ExecutionContext {

    /**
     * @return 规则实例ID
     */
    String getInstanceId();

    /**
     * 获取日志接口,通过此日志接口打印的日志可进行试试日志展示和记录
     *
     * @return 日志接口
     */
    Logger getLogger();

    /**
     * 同{@link ExecutionContext#getLogger()}
     *
     * @return 日志接口
     */
    default Logger logger() {
        return getLogger();
    }

    /**
     * 获取任务信息
     *
     * @return 任务信息
     */
    ScheduleJob getJob();

    /**
     * 触发事件,此方法永远返回{@link Mono#empty()}
     *
     * @param event 事件标识
     * @param data  规则数据
     * @param <T>   适配empty 泛型
     * @return Mono
     */
    <T> Mono<T> fireEvent(@Nonnull String event, @Nonnull RuleData data);

    default <T> Mono<T> fireEvent(@Nonnull String event,
                                  @Nonnull Supplier<RuleData> supplier) {
        return fireEvent(event, supplier.get());
    }

    /**
     * 触发error,此方法永远返回{@link Mono#empty()},此操作也会触发{@link org.jetlinks.rule.engine.api.RuleConstants.Event#error}事件
     *
     * @param e          异常信息
     * @param sourceData 规则数据
     * @param <T>        适配empty 泛型
     * @return Mono
     */
    <T> Mono<T> onError(@Nullable Throwable e, @Nullable RuleData sourceData);


    /**
     * 异步触发错误消息，在某些条件需要手动触发错误时,可以使用此方法。
     * <pre>
     *     mono
     *     .switchIfEmpty(context.onError(RuntimeException::new,data))
     * </pre>
     *
     * @param e          异常提供信息
     * @param sourceData 规则数据
     * @param <T>        适配empty 泛型
     * @return Mono
     */
    default <T> Mono<T> onError(@Nonnull Supplier<Throwable> e, @Nullable RuleData sourceData) {
        return Mono.defer(() -> onError(e.get(), sourceData));
    }

    /**
     * 获取输入接口，用于从上游接收数据
     *
     * @return 输入接口
     */
    Input getInput();

    /**
     * 获取输出接口，用于向下游节点输出数据
     *
     * @return 输出接口
     */
    Output getOutput();

    /**
     * 停止规则
     *
     * @param code    停止原因码
     * @param message 消息
     * @return Mono
     */
    Mono<Void> shutdown(String code, String message);

    /**
     * 创建规则数据,通常用于规则产生新数据时使用.
     *
     * @param data 原始数据
     * @return 规则数据
     */
    RuleData newRuleData(Object data);

    /**
     * 创建规则数据,通常用于基于规则输入生成新的数据.
     *
     * @param source 输入的源数据
     * @param data   需要输出的数据
     * @return 规则数据
     */
    RuleData newRuleData(RuleData source, Object data);


    /**
     * 监听停止事件
     *
     * @param runnable 事件监听器
     */
    void onShutdown(Runnable runnable);

    /**
     * @return 是否为debug模式
     */
    boolean isDebug();

    /**
     * @return 获取全局作用域
     */
    GlobalScope global();

    /**
     * 获取上下文作用域
     *
     * @param id 上下文ID
     * @return 上下文作用域
     */
    default ContextScope scope(String id) {
        return flow().context(id);
    }

    /**
     * 根据规则数据来获取上下文作用域,默认使用{@link RuleData#getContextId()}作为上下文ID
     *
     * @param ruleData 规则数据
     * @return 上下文作用域
     */
    default ContextScope scope(RuleData ruleData) {
        return scope(ruleData.getContextId());
    }

    /**
     * 获取当前规则节点作用域
     *
     * @return 节点作用域
     */
    default NodeScope node() {
        return node(getJob().getNodeId());
    }

    /**
     * 获取指定节点的作用域
     *
     * @param id 节点ID
     * @return 节点作用域
     */
    default NodeScope node(String id) {
        return flow().node(id);
    }

    /**
     * 获取当前规则流作用域
     *
     * @return 规则流作用域
     */
    default FlowScope flow() {
        return flow(getInstanceId());
    }

    /**
     * 获取指定的规则流作用域
     *
     * @param id 规则流ID
     * @return 规则流作用域
     */
    default FlowScope flow(String id) {
        return global().flow(id);
    }

}
