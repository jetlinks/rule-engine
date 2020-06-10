package org.jetlinks.rule.engine.defaults;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.events.EventBus;
import org.jetlinks.rule.engine.api.executor.Input;
import reactor.core.publisher.Flux;

import java.util.List;

@AllArgsConstructor
public class EventBusInput implements Input {

    private final String instanceId;

    private final String nodeId;

    //输入节点
    private final List<String> inputNodes;

    private final EventBus bus;

    @Override
    public Flux<RuleData> subscribe() {
        return bus.subscribe("/rule/engine/" + instanceId + "/" + nodeId + "/input", RuleData.class);
    }

    @Override
    public void close() {

    }
}
