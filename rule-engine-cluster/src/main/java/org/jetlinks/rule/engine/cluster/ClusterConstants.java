package org.jetlinks.rule.engine.cluster;

public interface ClusterConstants {

    interface Topics {

        static String workerPrefix(String workerId) {
            return "/rule-engine/cluster-worker/" + workerId;
        }

        static String schedulerPrefix(String scheduler) {
            return "/rule-engine/cluster-scheduler/" + scheduler;
        }

        static String createTask(String workerId) {
            return workerPrefix(workerId) + "/create_task";
        }

        static String getWorkerSupportExecutors(String workerId) {
            return workerPrefix(workerId) + "/executor/supports";
        }

        static String getWorkerState(String workerId) {
            return workerPrefix(workerId) + "/state";
        }

        static String setTaskJob(String workerId, String taskId) {
            return workerPrefix(workerId) + "/task/" + taskId + "/set_job";
        }

        static String getLastStateTime(String workerId, String taskId) {
            return workerPrefix(workerId) + "/task/" + taskId + "/last_state_time";
        }

        static String getStartTime(String workerId, String taskId) {
            return workerPrefix(workerId) + "/task/" + taskId + "/last_start_time";
        }

        static String taskOperation(String workerId, String taskId) {
            return workerPrefix(workerId) + "/task/" + taskId + "/operation";
        }

        static String taskExecute(String workerId, String taskId) {
            return workerPrefix(workerId) + "/task/" + taskId + "/execute";
        }

        static String getTaskState(String workerId, String taskId) {
            return workerPrefix(workerId) + "/task/" + taskId + "/state";
        }

        static String getSchedulerWorkers(String schedulerId) {
            return schedulerPrefix(schedulerId) + "/workers";
        }

        static String scheduleJob(String schedulerId) {
            return schedulerPrefix(schedulerId) + "/schedule";
        }

        static String shutdown(String schedulerId) {
            return schedulerPrefix(schedulerId) + "/shutdown";
        }

        static String totalScheduling(String schedulerId) {
            return schedulerPrefix(schedulerId) + "/scheduling-total";
        }


        static String getScheduleJobs(String schedulerId) {
            return schedulerPrefix(schedulerId) + "/scheduling";
        }

        static String getAllScheduleJobs(String schedulerId) {
            return schedulerPrefix(schedulerId) + "/scheduling-all";
        }

        static String canScheduleJob(String schedulerId) {
            return schedulerPrefix(schedulerId) + "/schedulable";
        }


    }
}
