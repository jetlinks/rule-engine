package org.jetlinks.rule.engine.executor.node.device;

import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.MessageType;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodec;
import org.jetlinks.rule.engine.api.RuleDataCodecSupplier;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public class RuleDeviceMessageCodec implements RuleDataCodec<Message> {


    static {
        RuleDeviceMessageCodec codec = new RuleDeviceMessageCodec();
        RuleDataCodecs.register(new RuleDataCodecSupplier() {
            @Override
            public boolean isSupport(Class type) {
                return Message.class.isAssignableFrom(type);
            }

            @Override
            public RuleDataCodec getCodec() {
                return codec;
            }
        });
    }

    static void load() {
    }

    @Override
    public Object encode(Message data, Feature... features) {
        return data;
    }

    @Override
    public Flux<Message> decode(RuleData data, Feature... features) {
        Object nativeData = data.getData();
        if (nativeData instanceof Message) {
            return Flux.just((Message) nativeData);
        }
        return data
                .dataToMap()
                .flatMap(map -> this.decode(map, features));
    }

    private Mono<Message> decode(Map<String, Object> data, Feature... features) {

        return Mono.defer(() -> {
            MessageType type = Feature.find(MessageTypeFeature.class, features)
                    .map(MessageTypeFeature::getMessageType)
                    .orElseGet(() -> MessageType.of(data).orElse(null));

            return Mono.justOrEmpty(type).map((t)->(Message)t.convert(data));
        });
    }

}
