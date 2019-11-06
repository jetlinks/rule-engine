package org.jetlinks.rule.engine.executor.node.mqtt;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.SimpleMqttMessage;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodec;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MqttRuleDataCodec implements RuleDataCodec<MqttMessage> {

    static {
        RuleDataCodecs.register(MqttMessage.class, new MqttRuleDataCodec());
    }

    static void load() {

    }


    @Override
    public Object encode(MqttMessage message, Feature... features) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("topic", message.getTopic());
        payload.put("will", message.isWill());
        payload.put("qos", message.getQosLevel());
        payload.put("dup", message.isDup());
        payload.put("retain", message.isRetain());
        PayloadType payloadType = Arrays.stream(features)
                .filter(PayloadType.class::isInstance)
                .map(PayloadType.class::cast)
                .findFirst()
                .orElse(PayloadType.STRING);

        payload.put("payload", payloadType.read(message.getPayload()));
        payload.put("deviceId", message.getDeviceId());
        return payload;
    }

    @Override
    public Flux<MqttMessage> decode(RuleData data, Feature... features) {
        if (data.getData() instanceof MqttMessage) {
            return Flux.just(((MqttMessage) data.getData()));
        }
        return data
                .dataToMap()
                .filter(map -> map.containsKey("topic") && map.containsKey("payload"))
                .map(map -> {
                    Object payload = map.get("payload");
                    ByteBuf byteBuf = null;
                    if (payload instanceof Map || payload instanceof List) {
                        payload = JSON.toJSONString(payload);
                    }
                    if (payload instanceof ByteBuf) {
                        byteBuf = ((ByteBuf) payload);
                    } else if (payload instanceof ByteBuffer) {
                        byteBuf = Unpooled.wrappedBuffer(((ByteBuffer) payload));
                    }

                    if (payload instanceof String) {
                        payload = ((String) payload).getBytes();
                    }
                    if (payload instanceof byte[]) {
                        byteBuf = Unpooled.wrappedBuffer(((byte[]) payload));
                    }
                    if (byteBuf == null) {
                        throw new UnsupportedOperationException("unsupported payload :" + payload);
                    }
                    Integer qos = (Integer) map.get("qos");

                    return SimpleMqttMessage
                            .builder()
                            .deviceId((String) map.get("deviceId"))
                            .topic((String) map.get("topic"))
                            .dup(Boolean.TRUE.equals(map.get("dup")))
                            .will(Boolean.TRUE.equals(map.get("will")))
                            .retain(Boolean.TRUE.equals(map.get("retain")))
                            .qosLevel(qos == null ? 0 : qos)
                            .payload(byteBuf)
                            .build();
                });
    }
}
