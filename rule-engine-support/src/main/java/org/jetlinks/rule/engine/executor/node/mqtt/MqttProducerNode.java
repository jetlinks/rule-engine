package org.jetlinks.rule.engine.executor.node.mqtt;

import lombok.AllArgsConstructor;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.SimpleMqttMessage;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.CommonExecutableRuleNodeFactoryStrategy;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.function.Function;

@AllArgsConstructor
public class MqttProducerNode extends CommonExecutableRuleNodeFactoryStrategy<MqttClientConfiguration> {

    private MqttClientManager clientManager;

    @Override
    public Function<RuleData, Publisher<Object>> createExecutor(ExecutionContext context, MqttClientConfiguration config) {
        return ruleData -> clientManager
                .getMqttClient(config.getClientId())
                .flatMap(client -> this.convertMessage(ruleData, config)
                        .flatMap(client::publish)
                        .all(r -> r));
    }


    protected Flux<MqttMessage> convertMessage(RuleData message, MqttClientConfiguration config) {
        return Flux.fromIterable(config.getTopics())
                .map(topic -> SimpleMqttMessage.builder()
                        .payload(config.getPayloadType().write(message.getData()))
                        .topic(topic)
                        .build());
    }

    @Override
    public String getSupportType() {
        return "mqtt-client-producer";
    }
}
