package org.jetlinks.rule.engine.cluster.stream;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.stream.Input;
import org.jetlinks.rule.engine.cluster.Queue;

import java.util.function.Consumer;

@Getter
@AllArgsConstructor
public class QueueInput implements Input {

    private Queue<RuleData> queue;

    @Override
    public void accept(Consumer<RuleData> accept) {
        queue.accept(accept);
    }

    @Override
    public boolean acceptOnce(Consumer<RuleData> accept) {
        return queue.acceptOnce(accept);
    }

    @Override
    public void close() {
        queue.stop();
    }
}
