package org.jetlinks.rule.engine.api;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * 任务,对应运行中规则的一个节点。
 *
 * @author zhouhao
 * @since 1.0.4
 */
public interface Task {

    /**
     * 唯一ID
     *
     * @return ID
     */
    String getId();

    /**
     * @return 名称
     */
    String getName();

    /**
     * @return 工作器ID
     */
    String getWorkerId();

    /**
     *
     * @return 调度器ID
     */
    String getSchedulerId();

    /**
     * 获取任务信息,请勿修改此任务信息的属性,修改了也没用。
     *
     * @return 任务信息
     */
    ScheduleJob getJob();

    /**
     * 设置任务信息,请设置配套的任务信息
     *
     * @param job 任务信息
     * @return empty Mono
     */
    Mono<Void> setJob(ScheduleJob job);

    /**
     * 重新加载任务,如果配置发生变化,将重启任务.
     *
     * @return empty Mono
     */
    Mono<Void> reload();

    /**
     * 启动,开始执行任务
     *
     * @return empty Mono
     */
    Mono<Void> start();

    /**
     * 暂停执行任务
     *
     * @return empty Mono
     */
    Mono<Void> pause();

    /**
     * 停止任务,于暂停不同等的是,停止后将进行清理资源等操作,
     * 通常在停止规则时或者调度器进行负载均衡时.
     *
     * @return empty Mono
     */
    Mono<Void> shutdown();

    /**
     * 执行任务
     *
     * @return 结果
     */
    Mono<Void> execute(Publisher<RuleData> data);

    /**
     * 获取任务状态
     *
     * @return 状态
     */
    Mono<State> getState();

    /**
     * 设置debug,开启debug后,不同的执行器可能有不同的操作,通常是打印更多的日志信息等操作。
     *
     * @param debug 是否开启debug
     * @return empty Mono
     */
    Mono<Void> debug(boolean debug);

    /**
     * @return 上一次状态变更时间
     */
    Mono<Long> getLastStateTime();

    /**
     * @return 启动时间
     */
    Mono<Long> getStartTime();

    /**
     * 创建任务快照
     *
     * @return 任务快照
     */
    default Mono<TaskSnapshot> dump() {
        return Mono.zip(getState(), getLastStateTime(), getStartTime())
                .map(tp3 -> {
                    TaskSnapshot snapshot = new TaskSnapshot();
                    snapshot.setId(getId());
                    snapshot.setInstanceId(getJob().getInstanceId());
                    snapshot.setJob(getJob());
                    snapshot.setLastStateTime(tp3.getT2());
                    snapshot.setState(tp3.getT1());
                    snapshot.setWorkerId(getWorkerId());
                    snapshot.setSchedulerId(getSchedulerId());
                    snapshot.setStartTime(tp3.getT3());
                    return snapshot;
                });
    }

    default boolean isSameTask(TaskSnapshot snapshot) {
        return this.getWorkerId().equals(snapshot.getWorkerId())
                && this.getJob().getNodeId().equals(snapshot.getJob().getNodeId());
    }

    enum State {
        //运行中
        running,
        //已暂停
        paused,
        //已停止
        shutdown,
        //未知,可能节点挂了,也可能网络问题状态不一致
        unknown
    }
}
