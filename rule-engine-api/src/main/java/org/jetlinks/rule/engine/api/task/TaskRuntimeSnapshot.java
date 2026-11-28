package org.jetlinks.rule.engine.api.task;

import lombok.Getter;
import org.apache.commons.collections.CollectionUtils;
import lombok.Setter;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;

import java.io.Serializable;

/** 当前任务的轻量只读快照，不包含配置、输入或恢复状态。@since 1.3.3 */
@Getter
@Setter
public class TaskRuntimeSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String instanceId;
    private String nodeId;
    private String workerId;
    private Task.State state;
    // 从任务的实际上下游边界读取，不依赖显式 start/end 标记。
    private Boolean entry;
    private Boolean terminal;
    private Long buffered;
    private TaskExecutionMetrics metrics;
    private OutputMetrics outputMetrics;

    public static TaskRuntimeSnapshot of(Task task, Task.State state, TaskExecutionMetrics metrics) {
        ScheduleJob job = task.getJob();
        TaskRuntimeSnapshot snapshot = new TaskRuntimeSnapshot();
        snapshot.id = task.getId();
        snapshot.instanceId = job.getInstanceId();
        snapshot.nodeId = job.getNodeId();
        snapshot.workerId = task.getWorkerId();
        snapshot.state = state;
        snapshot.entry = CollectionUtils.isEmpty(job.getInputs()) && CollectionUtils.isEmpty(job.getEvents());
        snapshot.terminal = CollectionUtils.isEmpty(job.getOutputs()) && CollectionUtils.isEmpty(job.getEventOutputs());
        snapshot.metrics = metrics;
        return snapshot;
    }
}
