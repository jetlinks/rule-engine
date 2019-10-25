package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.cluster.ClusterQueue;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

@Getter
@Setter
@Slf4j
public class ClusterRuleInstanceContext implements RuleInstanceContext {

    private String id;

    private ClusterQueue<RuleData> inputQueue;

    private Function<String, ClusterQueue<RuleData>> queueGetter;

    private String syncReturnNodeId;

    private org.jetlinks.core.cluster.ClusterManager clusterManager;

    private Supplier<RuleInstanceState> stateSupplier;

    private Supplier<Mono<Void>> onStop;
    private Supplier<Mono<Void>> onStart;

    private long syncTimeout = 30_000;

    private Map<String, FluxProcessor<RuleData, RuleData>> syncFutures = new ConcurrentHashMap<>();

    private ClusterQueue<RuleData> getQueue(RuleData ruleData) {
        return Optional.ofNullable(queueGetter)
                .flatMap(getter -> RuleDataHelper
                        .getStartWithNodeId(ruleData)
                        .map(getter))
                .orElse(inputQueue);
    }

    @Override
    public Flux<RuleData> execute(Publisher<RuleData> data) {
        Set<String> ids = new HashSet<>();
        return Flux
                .from(data)
                .concatMap(ruleData -> {
                    if (!RuleDataHelper.isSync(ruleData)) {
                        RuleDataHelper.markSyncReturn(ruleData, syncReturnNodeId);
                    }
                    ruleData.setAttribute("fromServer",clusterManager.getCurrentServerId());
                    EmitterProcessor<RuleData> processor = EmitterProcessor.create(true);
                    syncFutures.put(ruleData.getId(), processor);
                    Flux<RuleData> flux = processor.map(Function.identity());
                    return getQueue(ruleData)
                            .add(Mono.just(ruleData))
                            .flatMapMany(s -> {
                                if (!s) {
                                    return Flux.empty();
                                }
                                return flux;
                            });
                })
                .timeout(Duration.ofMillis(syncTimeout))
                .doFinally(s -> ids.forEach(syncFutures::remove));
    }

    @Override
    public Mono<Void> start() {
        if (null == onStart) {
            return Mono.empty();
        }
        return onStart.get();
    }

    protected void executeResult(RuleData data) {

        Optional.ofNullable(syncFutures.get(data.getId()))
                .ifPresent(processor -> processor.onNext(data));
    }

    protected void executeComplete(RuleData data) {
        Optional.ofNullable(syncFutures.remove(data.getId()))
                .ifPresent(Subscriber::onComplete);
    }

    @Override
    public Mono<Void> stop() {
        if (null == onStop) {
            return Mono.empty();
        }
        return onStop.get();
    }

    @Override
    public RuleInstanceState getState() {
        return stateSupplier.get();
    }


}
