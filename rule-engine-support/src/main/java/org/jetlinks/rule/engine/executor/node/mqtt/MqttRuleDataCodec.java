package org.jetlinks.rule.engine.executor.node.mqtt;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.SimpleMqttMessage;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodec;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
import org.jetlinks.supports.utils.MqttTopicUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
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
        PayloadType payloadType = Feature.find(PayloadType.class, features).orElseGet(() -> PayloadType.valueOf(message.getPayloadType().name()));
        Feature.find(TopicVariables.class, features)
                .map(TopicVariables::getVariables)
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(list -> list.stream()
                        .map(str -> MqttTopicUtils.getPathVariables(str, message.getTopic()))
                        .reduce((m1, m2) -> {
                            m1.putAll(m2);
                            return m1;
                        }))
                .ifPresent(vars -> payload.put("vars", vars));


        payload.put("payload", payloadType.read(message.getPayload()));
        payload.put("deviceId", message.getDeviceId());


        return payload;
    }

    @Override
    public Flux<MqttMessage> decode(RuleData data, Feature... features) {
        if (data.getData() instanceof MqttMessage) {
            return Flux.just(((MqttMessage) data.getData()));
        }
        MqttTopics topics = Feature.find(MqttTopics.class, features).orElse(null);

        return data
                .dataToMap()
                .filter(map -> map.containsKey("payload"))
                .flatMap(map -> {
                    if (topics != null && !map.containsKey("topic")) {
                        return Flux.fromIterable(topics.getTopics())
                                .flatMap(topic -> {
                                    Map<String, Object> copy = new HashMap<>();
                                    copy.put("topic", topic);
                                    copy.putAll(map);
                                    return Mono.just(copy);
                                })
                                .switchIfEmpty(Mono.error(() -> new UnsupportedOperationException("topic not set")));
                    }
                    return Flux.just(map);
                })
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
