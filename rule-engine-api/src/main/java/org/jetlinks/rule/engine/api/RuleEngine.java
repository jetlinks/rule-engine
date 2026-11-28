package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.scheduler.SchedulerRuntimeSnapshot;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.worker.Worker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 规则引擎
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleEngine {

    /**
     * 按规则身份批量读取各调度节点的轻量运行信息，不包含模型或输入。
     * @param instanceIds 最多64个规则身份，授权由业务入口负责
     * @return 每节点一个结果；不支持的实现明确失败，不能当成空任务
     * @since 1.3.3
     */
    default Flux<SchedulerRuntimeSnapshot> getRuntime(List<String> instanceIds) {
        return Flux.error(new UnsupportedOperationException("runtime projection is not supported"));
    }

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
