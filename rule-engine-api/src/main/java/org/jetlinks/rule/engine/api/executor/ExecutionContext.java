package org.jetlinks.rule.engine.api.executor;


import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import reactor.core.publisher.Mono;

/**
 * 规则节点执行上下文
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface ExecutionContext {

    /**
     * @return 规则实例ID
     */
    String getInstanceId();

    /**
     * @return 规则节点ID
     */
    String getNodeId();

    /**
     * @return 日志
     */
    Logger logger();

    /**
     * 数据输入接口,可通过观察者模式监听数据传入
     *
     * @return 数据输入接口
     */
    Input getInput();

    /**
     * 数据输出接口,当处理完数据后,将数据输出,进入下面当执行流程
     *
     * @return 数据输出接口
     */
    Output getOutput();

    /**
     * 触发事件
     *
     * @param event 事件名称
     * @param data  数据
     * @see org.jetlinks.rule.engine.api.events.RuleEvent
     */
    Mono<Void> fireEvent(String event, RuleData data);

    /**
     * 当执行发生异常时调用
     *
     * @param data 数据
     * @param e    异常信息
     * @see org.jetlinks.rule.engine.api.events.RuleEvent#NODE_EXECUTE_FAIL
     */
    Mono<Void> onError(RuleData data, Throwable e);

    /**
     * 停止规则
     */
    void stop();

    /**
     * 监听规则停止,当规则停止时触发{@link Runnable#run()}
     *
     * @param listener 监听器
     */
    void onStop(Runnable listener);
}
