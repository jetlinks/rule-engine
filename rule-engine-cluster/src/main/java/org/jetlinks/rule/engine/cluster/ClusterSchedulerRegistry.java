package org.jetlinks.rule.engine.cluster;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.event.EventBus;
import org.jetlinks.core.event.Subscription;
import org.jetlinks.core.rpc.RpcServiceFactory;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.cluster.scheduler.RemoteScheduler;
import reactor.core.Disposable;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Slf4j
public class ClusterSchedulerRegistry implements SchedulerRegistry {

    //本地调度器
    private final Set<Scheduler> localSchedulers = new ConcurrentSkipListSet<>(Comparator.comparing(Scheduler::getId));
    //远程调度器,在集群其他节点上的调度器
    private final Map<String, RemoteScheduler> remoteSchedulers = new ConcurrentHashMap<>();

    private final EmitterProcessor<Scheduler> joinProcessor = EmitterProcessor.create(Integer.MAX_VALUE, false);
    private final EmitterProcessor<Scheduler> leaveProcessor = EmitterProcessor.create(Integer.MAX_VALUE, false);

    private final FluxSink<Scheduler> joinSink = joinProcessor.sink();
    private final FluxSink<Scheduler> leaveSink = leaveProcessor.sink();

    private final List<Disposable> disposables = new CopyOnWriteArrayList<>();

    private final EventBus eventBus;
    private final RpcServiceFactory serviceFactory;

    @Setter
    private Duration keepaliveInterval = Duration.ofSeconds(10);

    public ClusterSchedulerRegistry(EventBus eventBus, RpcServiceFactory serviceFactory) {
        this.eventBus = eventBus;
        this.serviceFactory = serviceFactory;
    }

    public void setup() {
        if (!disposables.isEmpty()) {
            return;
        }
        joinProcessor.subscribe(scheduler -> {
            RemoteScheduler old = remoteSchedulers.put(scheduler.getId(), ((RemoteScheduler) scheduler));
            if (old != null) {
                old.dispose();
            }
            log.debug("remote scheduler join:{}", scheduler.getId());
        });
        leaveProcessor.subscribe(scheduler -> {
            log.debug("remote scheduler leave:{}", scheduler.getId());
            scheduler.dispose();
        });

        disposables.add(
                eventBus
                        .subscribe(Subscription.of("rule-engine.register", "/rule-engine/cluster-scheduler/keepalive", Subscription.Feature.broker), String.class)
                        .filter(id -> !remoteSchedulers.containsKey(id))
                        .doOnNext(id -> {
                            RemoteScheduler scheduler = new RemoteScheduler(id, serviceFactory);
                            scheduler.init();
                            joinSink.next(scheduler);
                            publishLocal().subscribe(); //有节点上线，广播本地节点。
                        })
                        .subscribe()
        );

        disposables.add(
                eventBus
                        .subscribe(Subscription.of("rule-engine.register", "/rule-engine/cluster-scheduler/leave", Subscription.Feature.broker), String.class)
                        .filter(remoteSchedulers::containsKey)
                        .flatMap(id -> Mono.justOrEmpty(remoteSchedulers.remove(id)))
                        .doOnNext(leaveSink::next)
                        .subscribe()
        );

        disposables.add(
                Flux.interval(keepaliveInterval)
                    .subscribe(ignore -> this
                            .publishLocal()
                            .then(Flux.fromIterable(remoteSchedulers.values())
                                      .filterWhen(scheduler -> scheduler
                                              .isNoAlive()
                                              .onErrorResume((err) -> Mono.just(true)))
                                      .doOnNext(leaveSink::next)
                                      .then())
                            .subscribe()
                    )
        );

        publishLocal().block();
    }

    private Mono<Void> publishLocal() {
        return eventBus
                .publish("/rule-engine/cluster-scheduler/keepalive", Flux
                        .fromIterable(localSchedulers)
                        .map(Scheduler::getId))
                .then();
    }

    public void cleanup() {
        eventBus.publish(
                "/rule-engine/cluster-scheduler/leave",
                Flux.fromIterable(localSchedulers).map(Scheduler::getId))
                .subscribe();

        remoteSchedulers.values().forEach(Disposable::dispose);
        disposables.forEach(Disposable::dispose);
        disposables.clear();
        remoteSchedulers.clear();

    }

    @Override
    public Flux<Scheduler> getSchedulers() {
        return Flux
                .just(localSchedulers, remoteSchedulers.values())
                .flatMapIterable(Function.identity());
    }

    @Override
    public Flux<Scheduler> handleSchedulerJoin() {
        return joinProcessor;
    }

    @Override
    public Flux<Scheduler> handleSchedulerLeave() {
        return leaveProcessor;
    }

    @Override
    public void register(Scheduler scheduler) {
        localSchedulers.add(scheduler);
        if (!disposables.isEmpty()) {
            publishLocal().subscribe();
        }
    }

    @Override
    public List<Scheduler> getLocalSchedulers() {
        return new ArrayList<>(localSchedulers);
    }
}
