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

    static final String NAMESPACE_SPLIT = "@";

    private final RpcManager rpcManager;

    private final Map<String, Scheduler> locals = new NonBlockingHashMap<>();

    private final Map<String, Scheduler> remotes = new NonBlockingHashMap<>();

    private final Sinks.Many<Scheduler> joinListener = Reactors.createMany();
    private final Sinks.Many<Scheduler> leaveListener = Reactors.createMany();

    private final String namespace;

    public ClusterRpcSchedulerRegistry(RpcManager rpcManager) {
        this(null, rpcManager);
    }

    public ClusterRpcSchedulerRegistry(String namespace, RpcManager rpcManager) {
        this.rpcManager = rpcManager;
        this.namespace = namespace;
        init();
    }

    void init() {

        rpcManager
            .getServices(SchedulerRpcService.class)
            .subscribe(rpc -> registerService(rpc.id(), rpc.service()));

        rpcManager
            .listen(SchedulerRpcService.class)
            .flatMap(event -> handleEvent(event).onErrorResume(err -> Mono.empty()))
            .subscribe(e -> {

            });
    }

    private Scheduler registerService(String id, SchedulerRpcService service) {
        String[] arr = id.split(NAMESPACE_SPLIT);
        Scheduler scheduler = null;
        try {
            //兼容没有使用命名空间的调度器
            if (arr.length == 1 || namespace == null) {
                if (remotes.put(id, scheduler = new ClusterRemoteScheduler(id, service)) == null) {
                    return scheduler;
                }
                return null;
            }
            //使用命名空间
            if (namespace.equals(arr[1])) {
                if (remotes.put(arr[0], scheduler = new ClusterRemoteScheduler(arr[0], service)) == null) {
                    return scheduler;
                }
            }
            return null;
        } finally {
            if (scheduler != null) {
                log.info("rule scheduler {} joined", scheduler.getId());
            }
        }

    }

    private Mono<Void> handleEvent(ServiceEvent event) {
        if (event.getType() == ServiceEvent.Type.added) {
            return rpcManager
                .getService(event.getServerNodeId(), event.getServiceId(), SchedulerRpcService.class)
                .doOnNext(rpc -> {
                    Scheduler scheduler = registerService(event.getServiceId(), rpc);

                    if (scheduler != null
                        && joinListener.currentSubscriberCount() > 0) {
                        joinListener.emitNext(scheduler, Reactors.emitFailureHandler());
                    }
                })
                .then();
        } else if (event.getType() == ServiceEvent.Type.removed) {
            String schedulerId;
            String[] arr = event.getServiceId().split(NAMESPACE_SPLIT);
            if (arr.length == 1) {
                schedulerId = event.getServiceId();
            } else {
                //不同命名空间.忽略
                if (!namespace.equals(arr[1])) {
                    return Mono.empty();
                }
                schedulerId = arr[0];
            }
            log.info("rule scheduler {} leaved", schedulerId);
            Scheduler scheduler = remotes.remove(schedulerId);
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

    private String createServiceId(String schedulerId) {
        //兼容没有使用命名空间的调度器
        if (namespace == null) {
            return schedulerId;
        }
        return schedulerId + NAMESPACE_SPLIT + namespace;
    }

    @Override
    public void register(Scheduler scheduler) {
        if (locals.containsKey(scheduler.getId())) {
            throw new IllegalStateException("scheduler " + scheduler.getId() + " already registered");
        }
        locals.put(scheduler.getId(), scheduler);

        rpcManager.registerService(createServiceId(scheduler.getId()),
                                   new SchedulerRpcServiceImpl(scheduler));

    }

}
