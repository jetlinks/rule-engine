package org.jetlinks.rule.engine.cluster.executor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.cluster.ClusterQueue;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.Output;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

@Getter
@AllArgsConstructor
@Slf4j
public class QueueOutput implements Output {

    private List<ConditionQueue> queues;

    @Override
    public Mono<Boolean> write(Publisher<RuleData> data) {
        return Flux.from(data)
                .concatMap(ruleData -> Flux.fromStream(queues.stream()
                        .filter(conditionQueue -> conditionQueue.predicate.test(ruleData))
                        .map(ConditionQueue::getQueue)
                        .map(queue -> queue.add(Mono.just(ruleData))))
                        .flatMap(Function.identity()))
                .all(r -> r);
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class ConditionQueue {
        private ClusterQueue<RuleData> queue;
        private Predicate<RuleData> predicate;
    }
}
