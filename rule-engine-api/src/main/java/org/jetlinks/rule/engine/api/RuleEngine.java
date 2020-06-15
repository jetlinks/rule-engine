package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
     * @param instanceId 实例ID
     * @param model      规则模型
     * @return 规则实例上下文
     */
    Flux<Task> startRule(String instanceId, RuleModel model);

    /**
     * 停止规则
     *
     * @param instanceId 实例ID
     * @return void
     */
    Mono<Void> shutdown(String instanceId);

    /**
     * 获取运行中的任务
     *
     * @param instance 实例ID
     * @return 规则实例上下文
     */
    Flux<Task> getTasks(String instance);

    /**
     * 获取全部Worker
     *
     * @return worker
     * @see ScheduleJob#getExecutor()
     */
    Flux<Worker> getWorkers();
}
