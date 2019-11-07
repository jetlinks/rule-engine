package org.jetlinks.rule.engine.executor.node.mqtt;

import lombok.AllArgsConstructor;
import org.hswebframework.web.dict.EnumDict;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.CommonExecutableRuleNodeFactoryStrategy;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@AllArgsConstructor
public class MqttClientNode extends CommonExecutableRuleNodeFactoryStrategy<MqttClientConfiguration> {

    private MqttClientManager clientManager;

    static {
        MqttRuleDataCodec.load();
    }

    @Override
    public Function<RuleData, Publisher<Object>> createExecutor(ExecutionContext context, MqttClientConfiguration config) {

        if (!EnumDict.in(ClientType.producer, config.getClientType())) {
            return Mono::just;
        }
        return ruleData -> clientManager
                .getMqttClient(config.getClientId())
                .flatMap(client -> this.convertMessage(ruleData, config)
                        .flatMap(client::publish)
                        .all(r -> r));
    }

    protected Flux<MqttMessage> convertMessage(RuleData message, MqttClientConfiguration config) {
        return RuleDataCodecs.getCodec(MqttMessage.class)
                .map(codec -> codec.decode(message, config.getPayloadType(), new MqttTopics(config.getTopics())).cast(MqttMessage.class))
                .orElseThrow(() -> new UnsupportedOperationException("unsupported decode message:{}" + message));
    }

    protected Mono<RuleData> convertMessage(MqttMessage message, MqttClientConfiguration config) {

        return Mono.just(RuleDataCodecs.getCodec(MqttMessage.class)
                .map(codec -> codec.encode(message, config.getPayloadType()))
                .map(RuleData::create)
                .orElseGet(() -> RuleData.create(message)));
    }


    @Override
    protected void onStarted(ExecutionContext context, MqttClientConfiguration config) {
        if (!EnumDict.in(ClientType.consumer, config.getClientType())) {
            return;
        }
        context.onStop(clientManager
                .getMqttClient(config.getClientId())
                .flatMapMany(client -> client.subscribe(config.getTopics()))
                .doOnNext(message -> context.logger().info("consume mqtt message:{}", message))
                .flatMap(message -> convertMessage(message, config))
                .flatMap(ruleData -> context.getOutput().write(Mono.just(ruleData)).thenReturn(ruleData))
                .doOnNext(ruleData -> context.fireEvent(RuleEvent.NODE_EXECUTE_RESULT, ruleData))
                .onErrorContinue((err, e) -> context.onError(RuleData.create("consume mqtt message error"), err).subscribe())
                .subscribe()::dispose);
    }

    @Override
    public String getSupportType() {
        return "mqtt-client";
    }
}
