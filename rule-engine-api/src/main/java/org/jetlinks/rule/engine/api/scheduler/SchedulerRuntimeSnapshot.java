package org.jetlinks.rule.engine.api.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.task.TaskRuntimeSnapshot;

import java.io.Serializable;
import java.util.List;

/** 一次有限批量读取的节点结果；失败不能解释为空任务。@since 1.3.3 */
@Getter
@Setter
public class SchedulerRuntimeSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int MAX_INSTANCES = 64;
    public static final int MAX_TASKS = 4096;

    private String schedulerId;
    private long sampledAt;
    private String status;
    private List<TaskRuntimeSnapshot> tasks = List.of();

    public static SchedulerRuntimeSnapshot ready(String id, List<TaskRuntimeSnapshot> tasks) {
        SchedulerRuntimeSnapshot snapshot = status(id, "ready");
        snapshot.tasks = tasks;
        return snapshot;
    }

    public static SchedulerRuntimeSnapshot status(String id, String status) {
        SchedulerRuntimeSnapshot snapshot = new SchedulerRuntimeSnapshot();
        snapshot.schedulerId = id;
        snapshot.sampledAt = System.currentTimeMillis();
        snapshot.status = status;
        return snapshot;
    }
}
