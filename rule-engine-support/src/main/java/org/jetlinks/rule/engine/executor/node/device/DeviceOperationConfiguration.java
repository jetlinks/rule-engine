package org.jetlinks.rule.engine.executor.node.device;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.hswebframework.web.utils.ExpressionUtils;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.*;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.executor.node.RuleNodeConfig;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Getter
@Setter
public class DeviceOperationConfiguration implements RuleNodeConfig {

    private String deviceId;

    private DeviceOperation operation;

    private DeviceMessageSendConfig sendConfig;

    private DefaultTransport transport;

    private boolean async = false;

    @Override
    public NodeType getNodeType() {
        return NodeType.MAP;
    }

    @Override
    public void setNodeType(NodeType nodeType) {

    }

    public Flux<? extends EncodedMessage> createEncodedMessage(DeviceOperator deviceOperator, RuleData ruleData) {
        return RuleDataCodecs.<EncodedMessage>getCodec(EncodedMessage.class)
                .map(codec -> codec.decode(ruleData, new DeviceOperatorFeature(deviceOperator), new TransportFeature(transport)))
                .orElseGet(Flux::empty);
    }


    public Flux<? extends Message> createDecodedMessage(RuleData ruleData, DeviceOperator deviceOperator) {

        return RuleDataCodecs.<Message>getCodec(Message.class)
                .map(codec -> codec.decode(ruleData, new DeviceOperatorFeature(deviceOperator), new TransportFeature(transport)))
                .orElseGet(Flux::empty);

    }

    public Flux<? extends Message> decode(DeviceOperator operator, RuleData ruleData) {
        return this.createDecodedMessage(ruleData, operator)
                .cast(Message.class)
                .switchIfEmpty(Flux.defer(() -> operator.getProtocol()
                        .flatMap(protocol -> protocol.getMessageCodec(this.getTransport()))
                        .flatMapMany(codec -> this
                                .createEncodedMessage(operator, ruleData)
                                .flatMap(msg -> codec.decode(new MessageDecodeContext() {
                                    @Override
                                    public EncodedMessage getMessage() {
                                        return msg;
                                    }

                                    @Override
                                    public DeviceOperator getDevice() {
                                        return operator;
                                    }
                                })))));
    }

    public Flux<? extends EncodedMessage> encode(DeviceOperator operator, RuleData ruleData) {

        return operator.getProtocol()
                .flatMap(protocol -> protocol.getMessageCodec(this.getTransport()))
                .flatMapMany(codec -> this
                        .createDecodedMessage(ruleData, operator)
                        .flatMap(msg -> codec.encode(new MessageEncodeContext() {
                            @Override
                            public Message getMessage() {
                                return msg;
                            }

                            @Override
                            public DeviceOperator getDevice() {
                                return operator;
                            }
                        })));

    }

    public Publisher<DeviceMessage> doSendMessage(DeviceOperator operator, RuleData ruleData) {

        return Flux.defer(() -> {
            DeviceMessage message = null;
            if (sendConfig != null) {
                message = sendConfig.convert(getDeviceId(ruleData), ruleData, async);
            }

            if (message == null) {
                return RuleDataCodecs.<DeviceMessage>getCodec(DeviceMessage.class)
                        .map(codec -> codec.decode(ruleData, new DeviceOperatorFeature(operator))
                                .switchIfEmpty(Flux.error(() -> new UnsupportedOperationException("cannot convert device message:" + ruleData))))
                        .map(msg -> operator
                                .messageSender()
                                .send(msg, reply -> (DeviceMessage) reply)
                                .cast(DeviceMessage.class))
                        .orElseThrow(() -> new UnsupportedOperationException("cannot convert device message:" + ruleData));
            } else {
                return operator
                        .messageSender()
                        .send(Mono.just(message), reply -> (DeviceMessage) reply)
                        .cast(DeviceMessage.class);
            }

        });
    }

    @SneakyThrows
    public String getDeviceId(RuleData ruleData) {
        Map<String, Object> ctx = RuleDataHelper.toContextMap(ruleData);
        if (deviceId.contains("${")) {
            return ExpressionUtils.analytical(deviceId, ctx, "spel");
        }
        return (String) ctx.getOrDefault(deviceId, deviceId);
    }
}
