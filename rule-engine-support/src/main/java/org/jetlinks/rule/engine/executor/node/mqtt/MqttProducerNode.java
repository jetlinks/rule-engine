package org.jetlinks.rule.engine.executor.node.mqtt;

import lombok.AllArgsConstructor;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
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
        return RuleDataCodecs.getCodec(MqttMessage.class)
                .map(codec -> codec.decode(message, config.getPayloadType()).cast(MqttMessage.class))
                .orElseThrow(() -> new UnsupportedOperationException("unsupported decode message:{}"+message))
                ;
    }

    @Override
    public String getSupportType() {
        return "mqtt-client-producer";
    }
}
