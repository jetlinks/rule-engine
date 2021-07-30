package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.worker.Worker;
import org.jetlinks.rule.engine.api.task.TaskSnapshot;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

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
     * 根据调度器ID获取任务快照
     *
     * @param schedulerId 调度器ID
     * @return 任务快照
     */
    Flux<TaskSnapshot> findBySchedulerId(String schedulerId);

    /**
     * 查询不是由指定调度器ID对应调度器调度的任务快照
     *
     * @param schedulerId 调度器ID
     * @return 任务快照
     */
    Flux<TaskSnapshot> findBySchedulerIdNotIn(Collection<String> schedulerId);

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
     * 根据ID删除Task
     * @param id ID
     * @return void
     */
    Mono<Void> removeTaskById(String id);



}
