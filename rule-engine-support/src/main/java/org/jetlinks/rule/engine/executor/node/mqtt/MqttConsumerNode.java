package org.jetlinks.rule.engine.executor.node.mqtt;

import lombok.AllArgsConstructor;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.CommonExecutableRuleNodeFactoryStrategy;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@AllArgsConstructor
public class MqttConsumerNode extends CommonExecutableRuleNodeFactoryStrategy<MqttClientConfiguration> {

    private MqttClientManager clientManager;

    static {
        MqttRuleDataCodec.load();
    }

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
                .doOnError(err -> context.logger().error("consume mqtt message error", err))
                .subscribe();
        context.onStop(disposable::dispose);
    }

    protected Mono<RuleData> convertMessage(MqttMessage message, MqttClientConfiguration config) {

        return Mono.just(RuleDataCodecs.getCodec(MqttMessage.class)
                .map(codec -> codec.encode(message,config.getPayloadType()))
                .map(RuleData::create)
                .orElseGet(() -> RuleData.create(message)));
    }

    @Override
    public String getSupportType() {
        return "mqtt-client-consumer";
    }
}
