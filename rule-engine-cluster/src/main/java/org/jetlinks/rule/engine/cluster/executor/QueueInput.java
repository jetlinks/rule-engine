package org.jetlinks.rule.engine.cluster.executor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.cluster.Queue;
import org.jetlinks.rule.engine.api.executor.Input;
import reactor.core.publisher.Flux;

import java.util.List;

@Getter
@AllArgsConstructor
public class QueueInput implements Input {

    private List<Queue<RuleData>> queues;

    @Override
    public Flux<RuleData> subscribe() {
        return Flux.fromIterable(queues)
                .concatMap(Queue::poll);
    }

    @Override
    public void close() {
        queues.forEach(Queue::stop);
    }
}
