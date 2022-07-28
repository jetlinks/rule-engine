package org.jetlinks.rule.engine.cluster.scheduler;

import io.scalecube.cluster.ClusterMessageHandler;
import io.scalecube.cluster.Member;
import io.scalecube.cluster.membership.MembershipEvent;
import io.scalecube.cluster.transport.api.Message;
import io.scalecube.reactor.RetryNonSerializedEmitFailureHandler;
import io.scalecube.services.ServiceCall;
import io.scalecube.services.ServiceInfo;
import lombok.extern.slf4j.Slf4j;
import org.jctools.maps.NonBlockingHashMap;
import org.jetlinks.rule.engine.api.scheduler.Scheduler;
import org.jetlinks.rule.engine.cluster.SchedulerRegistry;
import org.jetlinks.supports.scalecube.EmptyServiceMethodRegistry;
import org.jetlinks.supports.scalecube.ExtendedCluster;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class ClusterSchedulerRegistry implements SchedulerRegistry {
    private static final String REG_QUALIFIER = "rule/scheduler/reg";
    private static final String PULL_QUALIFIER = "rule/scheduler/pull";
    private static final String REG_FROM_HEADER = "from";
    private static final String SERVICE_SCHEDULER_ID_TAG = "schedulerId";

    private final ExtendedCluster cluster;

    private final ServiceCall serviceCall;

    private final Scheduler localScheduler;

    private final Map<String, ClusterRemoteScheduler> remotes = new NonBlockingHashMap<>();

    private final Sinks.Many<Scheduler> schedulerJoin = Sinks.many().multicast().directBestEffort();
    private final Sinks.Many<Scheduler> schedulerLeave = Sinks.many().multicast().directBestEffort();

    public ClusterSchedulerRegistry(ExtendedCluster cluster,
                                    ServiceCall serviceCall,
                                    Scheduler localScheduler) {
        this.cluster = cluster;
        this.serviceCall = serviceCall;
        this.localScheduler = localScheduler;
        init();
    }

    void init() {
        cluster.handler(cluster1 -> new ClusterMessageHandler() {
            @Override
            public void onGossip(Message gossip) {
                handleClusterMessage(gossip);
            }

            @Override
            public void onMessage(Message message) {
                handleClusterMessage(message);
            }

            @Override
            public void onMembershipEvent(MembershipEvent event) {
                if (event.isAdded() || event.isUpdated()) {
                    pullRemote(event.member())
                            .subscribe();
                }
                if (event.isRemoved() || event.isLeaving()) {
                    ClusterRemoteScheduler scheduler = remotes.remove(event.member().id());
                    if (null != scheduler) {
                        schedulerLeave.emitNext(scheduler, RetryNonSerializedEmitFailureHandler.RETRY_NON_SERIALIZED);
                    }
                }
            }
        });

        Flux.fromIterable(cluster.otherMembers())
            .flatMap(this::pullRemote)
            .blockLast();

    }

    private Mono<Void> pullRemote(Member member) {
        return cluster
                .send(member, Message
                        .withQualifier(PULL_QUALIFIER)
                        .header(REG_FROM_HEADER, cluster.member().id())
                        .data(localScheduler.getId())
                        .build())
                .onErrorResume(err -> {
                    log.error(err.getMessage(), err);
                    return Mono.empty();
                });
    }

    private Mono<Void> pushRemote(Member member) {
        return cluster
                .send(member, Message
                        .withQualifier(REG_QUALIFIER)
                        .header(REG_FROM_HEADER, cluster.member().id())
                        .data(localScheduler.getId())
                        .build());
    }

    private void handleClusterMessage(Message message) {
        if (Objects.equals(REG_QUALIFIER, message.qualifier()) || Objects.equals(PULL_QUALIFIER, message.qualifier())) {
            String from = message.header(REG_FROM_HEADER);
            String schedulerId = message.data();
            if(localScheduler.getId().equals(schedulerId)){
                log.warn("register same local scheduler [{}] from {}", schedulerId, cluster.member(from).orElse(null));
                return;
            }
            if (remotes.containsKey(from)) {
                return;
            }
            log.debug("register new scheduler {} from {}", schedulerId, cluster.member(from).orElse(null));
            @SuppressWarnings("all")
            SchedulerRpcService rpcService = serviceCall
                    .router((serviceRegistry, request) -> serviceRegistry
                            .lookupService(request)
                            .stream()
                            .filter(ref -> Objects.equals(schedulerId, ref
                                    .tags()
                                    .get(SERVICE_SCHEDULER_ID_TAG)))
                            .findFirst())
                    .methodRegistry(EmptyServiceMethodRegistry.INSTANCE)
                    .api(SchedulerRpcService.class);
            ClusterRemoteScheduler scheduler = new ClusterRemoteScheduler(schedulerId, rpcService);
            ClusterRemoteScheduler old = remotes.put(from, scheduler);
            if (old != null) {
                schedulerJoin.emitNext(scheduler,RetryNonSerializedEmitFailureHandler.RETRY_NON_SERIALIZED);
            }
        }

        if (Objects.equals(PULL_QUALIFIER, message.qualifier())) {
            String from = message.header(REG_FROM_HEADER);
            cluster.member(from)
                   .ifPresent(member -> pushRemote(member).subscribe());
        }
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
        return schedulerJoin.asFlux().onBackpressureBuffer();
    }

    @Override
    public Flux<Scheduler> handleSchedulerLeave() {
        return schedulerLeave.asFlux().onBackpressureBuffer();
    }

    @Override
    public void register(Scheduler scheduler) {
        throw new UnsupportedOperationException();
    }

    public static ServiceInfo createService(Scheduler scheduler) {
        SchedulerRpcServiceImpl impl = new SchedulerRpcServiceImpl(scheduler);
        return ServiceInfo
                .fromServiceInstance(impl)
                .tag(ClusterSchedulerRegistry.SERVICE_SCHEDULER_ID_TAG, scheduler.getId())
                .build();
    }

}
