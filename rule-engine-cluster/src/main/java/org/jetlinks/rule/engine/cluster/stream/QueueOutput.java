package org.jetlinks.rule.engine.cluster.stream;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.stream.Output;
import org.jetlinks.rule.engine.cluster.Queue;

@Getter
@AllArgsConstructor
public class QueueOutput implements Output {

    private Queue<RuleData> queue;

    @Override
    public void write(RuleData data) {
        queue.put(data);
    }
}
