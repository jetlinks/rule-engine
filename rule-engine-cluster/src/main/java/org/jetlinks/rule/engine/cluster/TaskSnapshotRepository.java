package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.Worker;
import org.jetlinks.rule.engine.api.TaskSnapshot;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 任务快照仓库
 *
 * @author zhouhao
 * @since 1.0.4
 */
public interface TaskSnapshotRepository {

    /**
     * 获取全部任务
     *
     * @return 全部任务
     */
    Flux<TaskSnapshot> findAllTask();

    /**
     * 根据规则实例ID获取任务快照
     *
     * @param instanceId 规则实例ID
     * @return 快照流
     */
    Flux<TaskSnapshot> findByInstanceId(String instanceId);

    /**
     * 根据workerId获取任务快照
     *
     * @param workerId workerId
     * @return 快照流
     * @see Worker#getId()
     */
    Flux<TaskSnapshot> findByWorkerId(String workerId);

    /**
     * 根据instanceId和workerId获取快照
     *
     * @param instanceId 实例ID
     * @param workerId   workerId
     * @return 快照流
     */
    Flux<TaskSnapshot> findByInstanceIdAndWorkerId(String instanceId, String workerId);

    /**
     * 根据规则实例ID和规则节点ID获取快照
     *
     * @param instanceId 实例ID
     * @param nodeId     节点ID
     * @return 快照流
     */
    Flux<TaskSnapshot> findByInstanceIdAndNodeId(String instanceId, String nodeId);

    /**
     * 保存快照
     *
     * @param snapshots 快照流
     * @return empty mono
     */
    Mono<Void> saveTaskSnapshots(Publisher<TaskSnapshot> snapshots);

    /**
     * 根据实例ID删除快照
     *
     * @param instanceId 实例ID
     * @return empty Mono
     */
    Mono<Void> removeTaskByInstanceId(String instanceId);

    /**
     * 根据实例ID和节点ID删除快照
     *
     * @param instanceId 实例ID
     * @param nodeId     节点ID
     * @return empty Mono
     */
    Mono<Void> removeTaskByInstanceIdAndNodeId(String instanceId, String nodeId);

    /**
     * 修改快照workerId
     *
     * @param instanceId 实例ID
     * @param nodeId     节点ID
     * @param workerId   workerId
     * @return empty mono
     */
    Mono<Void> changeWorkerId(String instanceId, String nodeId, String workerId);

    /**
     * 修改快照状态
     *
     * @param instanceId 实例ID
     * @param nodeId     节点ID
     * @param changeTime 状态变更时间
     * @param before     变更前状态
     * @param after      变更后状态
     * @return empty mono
     */
    Mono<Void> changeTaskSate(String instanceId, String nodeId, long changeTime, Task.State before, Task.State after);


}
