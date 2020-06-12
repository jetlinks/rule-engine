package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.Scheduler;
import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.cluster.scheduler.RemoteScheduler;
import reactor.core.Disposable;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class ClusterSchedulerRegistry implements SchedulerRegistry {

    private final Set<Scheduler> localSchedulers = new ConcurrentSkipListSet<>(Comparator.comparing(Scheduler::getId));
    private final Set<Scheduler> remoteSchedulers = new ConcurrentSkipListSet<>(Comparator.comparing(Scheduler::getId));

    private final EmitterProcessor<Scheduler> joinProcessor = EmitterProcessor.create(false);
    private final EmitterProcessor<Scheduler> leaveProcessor = EmitterProcessor.create(false);

    private final FluxSink<Scheduler> joinSink = joinProcessor.sink(FluxSink.OverflowStrategy.BUFFER);
    private final FluxSink<Scheduler> leaveSink = leaveProcessor.sink(FluxSink.OverflowStrategy.BUFFER);
    private final List<Disposable> disposables = new CopyOnWriteArrayList<>();

    private final EventBus eventBus;
    private final RpcService rpcService;

    public ClusterSchedulerRegistry(EventBus eventBus, RpcService rpcService) {
        this.eventBus = eventBus;
        this.rpcService = rpcService;
    }

    public void setup() {

        disposables.add(eventBus.subscribe("/rule-engine/cluster-scheduler/join", String.class)
                .map(id -> new RemoteScheduler(id, rpcService))
                .filter(scheduler -> !localSchedulers.contains(scheduler))
                .doOnNext(joinSink::next)
                .subscribe(remoteSchedulers::add));

        disposables.add(eventBus.subscribe("/rule-engine/cluster-scheduler/leave", String.class)
                .map(id -> new RemoteScheduler(id, rpcService))
                .filter(scheduler -> !localSchedulers.contains(scheduler))
                .doOnNext(leaveSink::next)
                .subscribe(remoteSchedulers::remove));

    }

    public void cleanup() {

        eventBus.publish(
                "/rule-engine/cluster-scheduler/leave",
                Flux.fromIterable(localSchedulers).map(Scheduler::getId))
                .subscribe();

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
    public Mono<Void> register(Scheduler scheduler) {
        localSchedulers.add(scheduler);
        return eventBus
                .publish("/rule-engine/cluster-scheduler/join", Mono.just(scheduler.getId()))
                .then();
    }
}
