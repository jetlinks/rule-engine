package org.jetlinks.rule.engine.cluster.executor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.Input;
import org.jetlinks.rule.engine.api.cluster.Queue;

import java.util.List;
import java.util.function.Consumer;

@Getter
@AllArgsConstructor
public class QueueInput implements Input {

    private List<Queue<RuleData>> queues;

    @Override
    public boolean acceptOnce(Consumer<RuleData> accept) {
        queues.forEach(queue -> queue.acceptOnce(accept));
        return true;
    }

    @Override
    public void close() {
        queues.forEach(Queue::stop);
    }
}
