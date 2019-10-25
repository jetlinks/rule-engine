package org.jetlinks.rule.engine.cluster.executor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.core.cluster.ClusterQueue;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.Input;
import reactor.core.publisher.Flux;

import java.util.List;

@Getter
@AllArgsConstructor
public class QueueInput implements Input {

    private List<ClusterQueue<RuleData>> queues;

    @Override
    public Flux<RuleData> subscribe() {
        return Flux.fromIterable(queues)
                .concatMap(ClusterQueue::subscribe);
    }

    @Override
    public void close() {
        queues.forEach(ClusterQueue::stop);
    }
}
