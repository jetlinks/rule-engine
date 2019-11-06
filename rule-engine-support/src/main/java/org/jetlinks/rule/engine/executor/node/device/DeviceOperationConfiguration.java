package org.jetlinks.rule.engine.executor.node.device;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.hswebframework.web.utils.ExpressionUtils;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.DefaultTransport;
import org.jetlinks.core.message.codec.EncodedMessage;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.executor.node.RuleNodeConfig;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class DeviceOperationConfiguration implements RuleNodeConfig {

    private String deviceId;

    private DeviceOperation operation;

    private ReadPropertyConfig readProperty;

    private DefaultTransport transport;

    private boolean async = false;

    @Override
    public NodeType getNodeType() {
        return NodeType.MAP;
    }

    @Override
    public void setNodeType(NodeType nodeType) {

    }

    public Flux<? extends EncodedMessage> createEncodedMessage(RuleData ruleData) {

        if (transport == DefaultTransport.MQTT
                || transport == DefaultTransport.MQTTS) {
            return RuleDataCodecs.<MqttMessage, MqttMessage>getCodec(MqttMessage.class)
                    .map(codec -> codec.decode(ruleData))
                    .orElseGet(Flux::empty);
        }

        return Flux.error(() -> new UnsupportedOperationException("unsupported data:" + ruleData));
    }


    public Flux<? extends Message> createDecodedMessage(RuleData ruleData) {

        return RuleDataCodecs.<Message, Message>getCodec(Message.class)
                .map(codec -> codec.decode(ruleData))
                .orElseGet(() -> Flux.error(new UnsupportedOperationException("unsupported data:" + ruleData)));

    }

    public Publisher<DeviceMessage> doSendMessage(DeviceOperator operator, RuleData ruleData) {

        DeviceMessage message = null;
        if (readProperty != null) {
            message = readProperty.convert(ruleData, async);
        }
        // TODO: 2019-11-06

        if (message == null) {
            return Flux.empty();
        }

        return operator
                .messageSender()
                .send(Mono.just(message), reply -> (DeviceMessage) reply)
                .cast(DeviceMessage.class);
    }

    @SneakyThrows
    public String getDeviceId(RuleData ruleData) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("attr", ruleData.getAttributes());
        ctx.put("data", ruleData.getData());
        ctx.put("ruleData", ruleData);
        ruleData.dataToMap().subscribe(ctx::putAll);
        if (deviceId.contains("${")) {
            return ExpressionUtils.analytical(deviceId, ctx, "spel");
        }
        return (String) ctx.getOrDefault(deviceId, deviceId);
    }
}
