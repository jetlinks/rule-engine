package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.jctools.maps.NonBlockingHashMap;
import org.jetlinks.core.rpc.RpcManager;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.cluster.SchedulerRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class ClusterRpcSchedulerRegistry implements SchedulerRegistry {


    private final RpcManager rpcManager;

    private final Scheduler localScheduler;

    private final Map<String, Scheduler> remotes = new NonBlockingHashMap<>();

    private final Sinks.Many<Scheduler> joinListener = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<Scheduler> leaveListener = Sinks.many().multicast().onBackpressureBuffer();


    public ClusterRpcSchedulerRegistry(RpcManager rpcManager,
                                       Scheduler localScheduler) {
        this.rpcManager = rpcManager;
        this.localScheduler = localScheduler;
        init();
    }

    void init() {

        rpcManager.registerService(
                localScheduler.getId(),
                new SchedulerRpcServiceImpl(localScheduler));

//        rpcManager
//                .listen(SchedulerRpcService.class)
//                .subscribe(e -> {
//                    if (e.getType() == ServiceEvent.Type.added) {
//                        rpcManager
//                                .getService(e.getServerNodeId(), SchedulerRpcService.class)
//                                .map(rpcService -> {
//                                    return new ClusterRemoteScheduler(e.getService(),rpcService);
//                                })
//                    }
//                })
    }


    @Override
    public List<Scheduler> getLocalSchedulers() {
        return Collections.singletonList(localScheduler);
    }

    @Override
    public Flux<Scheduler> getSchedulers() {
        return Flux.concat(
                Flux.fromIterable(getLocalSchedulers()),
                Flux.fromIterable(remotes.values())
        );
    }

    @Override
    public Flux<Scheduler> handleSchedulerJoin() {
        return joinListener.asFlux();
    }

    @Override
    public Flux<Scheduler> handleSchedulerLeave() {
        return leaveListener.asFlux();
    }

    @Override
    public void register(Scheduler scheduler) {
        throw new UnsupportedOperationException();
    }

}
