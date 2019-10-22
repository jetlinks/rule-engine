package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.api.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.cluster.Queue;
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

    private long startTime;

    private Queue<RuleData> inputQueue;

    private Function<String, Queue<RuleData>> queueGetter;

    private String syncReturnNodeId;

    private ClusterManager clusterManager;

    private Supplier<RuleInstanceState> stateSupplier;

    private Runnable onStop;
    private Runnable onStart;

    private long syncTimeout = 30_000;

    private Map<String, FluxProcessor<RuleData, RuleData>> syncFutures = new ConcurrentHashMap<>();

    private Queue<RuleData> getQueue(RuleData ruleData) {
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
                    EmitterProcessor<RuleData> processor = EmitterProcessor.create(true);
                    syncFutures.put(ruleData.getId(), processor);
                    Flux<RuleData> flux = processor.map(Function.identity());
                    return getQueue(ruleData)
                            .put(Mono.just(ruleData))
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
    public void start() {
        if (null != onStart) {
            onStart.run();
        }
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
    public void stop() {
        if (null != onStop) {
            onStop.run();
        }
    }

    @Override
    public RuleInstanceState getState() {
        return stateSupplier.get();
    }


}
