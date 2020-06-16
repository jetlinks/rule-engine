package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.cluster.task.TaskRpc;


public interface RpcMethods {

    @AllArgsConstructor
    @Getter
    enum Scheduler implements RpcMethods{

        getWorkers(Void.class, SchedulerRpc.WorkerInfo.class),
        schedule(ScheduleJob.class, TaskRpc.TaskInfo.class),
        shutdown(String.class, Void.class),
        getSchedulingJobs(String.class, TaskRpc.TaskInfo.class),
        ;
        private final Class<?> requestType;
        private final Class<?> responseType;


    }


}
