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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Slf4j
public class ClusterSchedulerRegistry implements SchedulerRegistry {

    //本地调度器
    private final Set<Scheduler> localSchedulers = new ConcurrentSkipListSet<>(Comparator.comparing(Scheduler::getId));
    //远程调度器,在集群其他节点上的调度器
    private final Set<RemoteScheduler> remoteSchedulers = new ConcurrentSkipListSet<>(Comparator.comparing(Scheduler::getId));

    private final EmitterProcessor<Scheduler> joinProcessor = EmitterProcessor.create(false);
    private final EmitterProcessor<Scheduler> leaveProcessor = EmitterProcessor.create(false);

    private final FluxSink<Scheduler> joinSink = joinProcessor.sink(FluxSink.OverflowStrategy.BUFFER);
    private final FluxSink<Scheduler> leaveSink = leaveProcessor.sink(FluxSink.OverflowStrategy.BUFFER);

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
        joinProcessor.subscribe(scheduler -> log.debug("remote scheduler join:{}", scheduler.getId()));
        leaveProcessor.subscribe(scheduler -> log.debug("remote scheduler leaved:{}", scheduler.getId()));

        disposables.add(
                eventBus.subscribe(Subscription.of("rule-engine.register", "/rule-engine/cluster-scheduler/keepalive", Subscription.Feature.broker), String.class)
                        .map(id -> new RemoteScheduler(id, serviceFactory))
                        .filter(scheduler -> !localSchedulers.contains(scheduler) && !remoteSchedulers.contains(scheduler))
                        .doOnNext(remoteScheduler -> {
                            remoteScheduler.init();
                            joinSink.next(remoteScheduler);
                            publishLocal().subscribe(); //有节点上线，广播本地节点。
                        })
                        .subscribe(
                                remoteSchedulers::add,
                                error -> log.error(error.getMessage(), error)
                        )
        );

        disposables.add(
                eventBus.subscribe(Subscription.of("rule-engine.register", "/rule-engine/cluster-scheduler/leave", Subscription.Feature.broker), String.class)
                        .map(id -> new RemoteScheduler(id, serviceFactory))
                        .filter(scheduler -> !localSchedulers.contains(scheduler))
                        .doOnNext(leaveSink::next)
                        .subscribe(remoteSchedulers::remove)
        );

        disposables.add(
                Flux.interval(keepaliveInterval)
                        .subscribe(ignore ->
                                Flux.fromIterable(remoteSchedulers)
                                        .filterWhen(scheduler -> scheduler
                                                .isNoAlive()
                                                .onErrorResume((err)->Mono.just(true)))
                                        .doOnNext(scheduler -> {
                                            scheduler.dispose();
                                            remoteSchedulers.remove(scheduler);
                                            leaveSink.next(scheduler);
                                        })
                                        .then(publishLocal())
                                        .subscribe())
        );

        publishLocal().subscribe();
    }

    private Mono<Void> publishLocal() {
        return eventBus
                .publish("/rule-engine/cluster-scheduler/keepalive", Flux.fromIterable(localSchedulers).map(Scheduler::getId))
                .then();
    }

    public void cleanup() {
        eventBus.publish(
                "/rule-engine/cluster-scheduler/leave",
                Flux.fromIterable(localSchedulers).map(Scheduler::getId))
                .subscribe();

        remoteSchedulers.forEach(Disposable::dispose);
        disposables.forEach(Disposable::dispose);
        disposables.clear();

    }

    @Override
    public Flux<Scheduler> getSchedulers() {
        return Flux
                .just(localSchedulers, remoteSchedulers)
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
