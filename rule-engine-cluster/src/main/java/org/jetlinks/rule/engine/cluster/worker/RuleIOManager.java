package org.jetlinks.rule.engine.cluster.worker;

import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scope.GlobalScope;
import org.jetlinks.rule.engine.api.task.Input;
import org.jetlinks.rule.engine.api.task.Output;

import java.util.Map;

/**
 * 规则引起IO管理器,用于创建输入输出等持久化对象
 *
 * @author zhouhao
 * @since 1.1.1
 */
public interface RuleIOManager {

    /**
     * 创建任务输入
     *
     * @param job 任务
     * @return 输入
     */
    Input createInput(ScheduleJob job);

    /**
     * 创建任务输出
     * @param job 任务
     * @return 输出
     */
    Output createOutput(ScheduleJob job);

    /**
     * 创建作用域名
     * @return 作用域
     */
    GlobalScope createScope();

    /**
     * 创建事件输出
     * @param job 任务
     * @return 事件输出
     */
    Map<String, Output> createEvent(ScheduleJob job);

}
