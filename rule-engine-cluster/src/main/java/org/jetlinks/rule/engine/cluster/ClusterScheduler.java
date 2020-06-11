package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.Scheduler;
import org.jetlinks.rule.engine.api.Worker;
import reactor.core.publisher.Flux;

public interface ClusterScheduler extends Scheduler {

    /**
     * 监听worker加入事件
     *
     * @return worker流
     */
    Flux<Worker> handleWorkerJoin();

    /**
     * 监听worker掉线事件
     *
     * @return worker流
     */
    Flux<Worker> handleWorkerLeave();

}
