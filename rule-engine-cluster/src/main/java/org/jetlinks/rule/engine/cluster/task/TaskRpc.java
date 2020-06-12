package org.jetlinks.rule.engine.cluster.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.ScheduleJob;
import org.jetlinks.rule.engine.api.Task;
import org.jetlinks.rule.engine.api.rpc.RpcDefinition;
import org.jetlinks.rule.engine.cluster.ClusterConstants;

public interface TaskRpc {

    static RpcDefinition<RuleData, Void> executeTask(String workerId, String taskId) {
        return RpcDefinition.ofNoResponse(ClusterConstants.Topics.taskExecute(workerId, taskId),
                RuleData.class
        );
    }

    static RpcDefinition<Void, Task.State> getTaskState(String workerId, String taskId) {
        return RpcDefinition.ofNoParameter(ClusterConstants.Topics.getTaskState(workerId, taskId),
                Task.State.class
        );
    }

    static RpcDefinition<TaskOperation, Void> taskOperation(String workerId, String taskId) {
        return RpcDefinition.ofNoResponse(ClusterConstants.Topics.taskOperation(workerId, taskId),
                TaskOperation.class
        );
    }

    static RpcDefinition<ScheduleJob, Void> setTaskJob(String workerId, String taskId) {
        return RpcDefinition.ofNoResponse(ClusterConstants.Topics.setTaskJob(workerId, taskId),
                ScheduleJob.class
        );
    }

    static RpcDefinition<Void, Long> getLastStateTime(String workerId, String taskId) {
        return RpcDefinition.ofNoParameter(ClusterConstants.Topics.getLastStateTime(workerId, taskId),
                long.class
        );
    }

    static RpcDefinition<Void, Long> getStartTime(String workerId, String taskId) {
        return RpcDefinition.ofNoParameter(ClusterConstants.Topics.getStartTime(workerId, taskId),
                long.class
        );
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    class TaskInfo {
        private String id;

        private String name;

        private String workerId;

        private ScheduleJob job;

    }

    enum TaskOperation {
        START,
        PAUSE,
        RELOAD,
        SHUTDOWN,
        ENABLE_DEBUG,
        DISABLE_DEBUG
    }


}
