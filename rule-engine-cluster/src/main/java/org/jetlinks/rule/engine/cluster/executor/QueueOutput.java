package org.jetlinks.rule.engine.cluster.executor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.api.executor.Output;
import org.jetlinks.rule.engine.api.cluster.Queue;

import java.util.List;
import java.util.function.Predicate;

@Getter
@AllArgsConstructor
public class QueueOutput implements Output {

    private List<ConditionQueue> queues;

    @Override
    public void write(RuleData data) {
        queues.stream()
                .filter(cd -> cd.predicate.test(data))
                .map(ConditionQueue::getQueue)
                .forEach(queue -> queue.put(data));
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class ConditionQueue {
        private Queue<RuleData>     queue;
        private Predicate<RuleData> predicate;
    }
}
