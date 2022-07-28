package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.jctools.maps.NonBlockingHashMap;
import org.jetlinks.core.rpc.RpcManager;
import org.jetlinks.core.rpc.ServiceEvent;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.cluster.SchedulerRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class ClusterRpcSchedulerRegistry implements SchedulerRegistry {

    private final RpcManager rpcManager;

    private final Map<String, Scheduler> locals = new NonBlockingHashMap<>();

    private final Map<String, Scheduler> remotes = new NonBlockingHashMap<>();

    private final Sinks.Many<Scheduler> joinListener = Reactors.createMany();
    private final Sinks.Many<Scheduler> leaveListener =Reactors.createMany();

    public ClusterRpcSchedulerRegistry(RpcManager rpcManager) {
        this.rpcManager = rpcManager;
        init();
    }

    void init() {

        rpcManager
                .getServices(SchedulerRpcService.class)
                .subscribe(rpc -> remotes.put(rpc.id(), new ClusterRemoteScheduler(rpc.id(), rpc.service())));

        rpcManager
                .listen(SchedulerRpcService.class)
                .flatMap(event -> handleEvent(event)
                        .onErrorResume(err -> Mono.empty()))
                .subscribe(e -> {

                });
    }

    private Mono<Void> handleEvent(ServiceEvent event) {
        if (event.getType() == ServiceEvent.Type.added) {
            return rpcManager
                    .getService(event.getServerNodeId(), event.getServiceId(), SchedulerRpcService.class)
                    .map(rpcService -> new ClusterRemoteScheduler(event.getServiceId(), rpcService))
                    .doOnNext(scheduler -> {
                        if (remotes.put(event.getServiceId(), scheduler) == null
                                && joinListener.currentSubscriberCount() > 0) {
                            joinListener.emitNext(scheduler,Reactors.emitFailureHandler());
                        }
                    })
                    .then();
        } else if (event.getType() == ServiceEvent.Type.removed) {
            Scheduler scheduler = remotes.remove(event.getServiceId());
            if (null != scheduler && leaveListener.currentSubscriberCount() > 0) {
                leaveListener.emitNext(scheduler, Reactors.emitFailureHandler());
            }
        }
        return Mono.empty();
    }

    @Override
    public List<Scheduler> getLocalSchedulers() {
        return new ArrayList<>(locals.values());
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
        if (locals.containsKey(scheduler.getId())) {
            throw new IllegalStateException("scheduler " + scheduler.getId() + " already registered");
        }
        locals.put(scheduler.getId(), scheduler);

        rpcManager.registerService(scheduler.getId(), new SchedulerRpcServiceImpl(scheduler));

    }

}
