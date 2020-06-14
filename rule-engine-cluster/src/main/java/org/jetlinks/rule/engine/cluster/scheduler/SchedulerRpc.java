package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.rpc.RpcDefinition;
import org.jetlinks.rule.engine.cluster.ClusterConstants;
import org.jetlinks.rule.engine.cluster.task.TaskRpc;

public interface SchedulerRpc {

    static RpcDefinition<Void, WorkerInfo> getWorkers(String schedulerId) {
        return RpcDefinition.ofNoParameter(ClusterConstants.Topics.getSchedulerWorkers(schedulerId),
                WorkerInfo.class
        );
    }

    static RpcDefinition<ScheduleJob, TaskRpc.TaskInfo> schedule(String schedulerId) {
        return RpcDefinition.of(ClusterConstants.Topics.scheduleJob(schedulerId),
                ScheduleJob.class,
                TaskRpc.TaskInfo.class
        );
    }

    static RpcDefinition<String, Void> shutdown(String schedulerId) {
        return RpcDefinition.ofNoResponse(ClusterConstants.Topics.shutdown(schedulerId),
                String.class
        );
    }

    static RpcDefinition<String, TaskRpc.TaskInfo> getSchedulingJobs(String schedulerId) {
        return RpcDefinition.of(ClusterConstants.Topics.getScheduleJobs(schedulerId),
                String.class,
                TaskRpc.TaskInfo.class
        );
    }

    static RpcDefinition<Void, TaskRpc.TaskInfo> getSchedulingAllJobs(String schedulerId) {
        return RpcDefinition.ofNoParameter(ClusterConstants.Topics.getAllScheduleJobs(schedulerId),
                TaskRpc.TaskInfo.class
        );
    }

    static RpcDefinition<Void, Long> getTotalTasks(String schedulerId) {
        return RpcDefinition.ofNoParameter(ClusterConstants.Topics.totalScheduling(schedulerId),
                long.class
        );
    }

    static RpcDefinition<ScheduleJob, Boolean> canSchedule(String schedulerId) {
        return RpcDefinition.of(ClusterConstants.Topics.canScheduleJob(schedulerId),
                ScheduleJob.class,
                boolean.class
        );
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    class WorkerInfo {
        private String id;

        private String name;

    }

}
