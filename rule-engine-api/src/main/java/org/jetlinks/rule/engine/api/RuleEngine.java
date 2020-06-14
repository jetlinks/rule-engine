package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.task.Task;
import reactor.core.publisher.Flux;

/**
 * 规则引擎
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleEngine {

    /**
     * 启动规则
     *
     * @param model 规则模型
     * @return 规则实例上下文
     */
    Flux<Task> startRule(String instanceId, RuleModel model);

    /**
     * 获取运行中的任务
     *
     * @param instance 实例ID
     * @return 规则实例上下文
     */
    Flux<Task> getTasks(String instance);

}
