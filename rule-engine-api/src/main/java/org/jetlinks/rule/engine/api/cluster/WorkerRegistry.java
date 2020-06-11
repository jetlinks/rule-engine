package org.jetlinks.rule.engine.api.cluster;

import org.jetlinks.rule.engine.api.Worker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * worker注册中心
 *
 * @author zhouhao
 * @since 1.0.4
 */
public interface WorkerRegistry {

    /**
     * 获取全部worker
     *
     * @return worker流
     */
    Flux<Worker> getWorkers();

    /**
     * 监听worker加入注册中心事件
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

    /**
     * 注册worker到注册中心
     *
     * @param worker worker
     * @return empty mono
     */
    Mono<Void> register(Worker worker);


}
