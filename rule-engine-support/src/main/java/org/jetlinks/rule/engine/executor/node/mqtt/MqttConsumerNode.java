package org.jetlinks.rule.engine.executor.node.mqtt;

import lombok.AllArgsConstructor;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.CommonExecutableRuleNodeFactoryStrategy;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@AllArgsConstructor
public class MqttConsumerNode extends CommonExecutableRuleNodeFactoryStrategy<MqttClientConfiguration> {

    private MqttClientManager clientManager;

    @Override
    public Function<RuleData, Publisher<Object>> createExecutor(ExecutionContext context, MqttClientConfiguration config) {
        return Mono::just;
    }

    @Override
    protected void onStarted(ExecutionContext context, MqttClientConfiguration config) {
        super.onStarted(context, config);

        Disposable disposable = clientManager
                .getMqttClient(config.getClientId())
                .flatMapMany(client -> client.subscribe(config.getTopics()))
                .doOnNext(message -> context.logger().info("consume mqtt message:{}", message))
                .flatMap(message -> context.getOutput().write(convertMessage(message, config)))
                .doOnError(err -> context.logger().error("consume mqtt message error:{}", err))
                .subscribe();
        context.onStop(disposable::dispose);
    }

    protected Mono<RuleData> convertMessage(MqttMessage message, MqttClientConfiguration config) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("topic", message.getTopic());
        payload.put("will", message.isWill());
        payload.put("qos", message.getQosLevel());
        payload.put("dup", message.isDup());
        payload.put("retain", message.isRetain());
        payload.put("payload", config.getPayloadType().read(message.getPayload()));
        return Mono.just(RuleData.create(payload));
    }

    @Override
    public String getSupportType() {
        return "mqtt-client-consumer";
    }
}
