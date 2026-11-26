package org.jetlinks.rule.engine.api.task;

import lombok.Getter;
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
    // null 表示旧任务未声明入口角色，不能猜测入口吞吐。
    private Boolean entry;
    private Boolean terminal;
    private Long buffered;
    private TaskExecutionMetrics metrics;

    public static TaskRuntimeSnapshot of(Task task, Task.State state, TaskExecutionMetrics metrics) {
        ScheduleJob job = task.getJob();
        TaskRuntimeSnapshot snapshot = new TaskRuntimeSnapshot();
        snapshot.id = task.getId();
        snapshot.instanceId = job.getInstanceId();
        snapshot.nodeId = job.getNodeId();
        snapshot.workerId = task.getWorkerId();
        snapshot.state = state;
        snapshot.entry = job.getStartNode() != null ? job.getStartNode()
            : job.getInputs() == null || job.getInputs().isEmpty() ? Boolean.TRUE : null;
        snapshot.terminal = job.getEndNode() != null ? job.getEndNode()
            : job.getOutputs() == null || job.getOutputs().isEmpty() ? Boolean.TRUE : null;
        snapshot.metrics = metrics;
        return snapshot;
    }
}
