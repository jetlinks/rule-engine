package org.jetlinks.rule.engine.executor.node.device;

import org.jetlinks.core.message.codec.EncodedMessage;
import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodec;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EncodedMessageCodec implements RuleDataCodec<EncodedMessage> {

    private static final Map<String, RuleDataCodec<EncodedMessage>> transportCodecs = new ConcurrentHashMap<>();

    static {
        RuleDataCodecs.register(EncodedMessage.class, new EncodedMessageCodec());
    }

    public static void register(Transport transport, RuleDataCodec<? extends EncodedMessage> codec) {
        transportCodecs.put(transport.getId(), (RuleDataCodec)codec);
    }

    @Override
    public Object encode(EncodedMessage data, Feature... features) {

        return Feature.find(TransportFeature.class, features)
                .map(TransportFeature::getTransport)
                .map(Transport::getId)
                .map(transportCodecs::get)
                .map(codec -> codec.encode(data, features))
                .orElseThrow(() -> new UnsupportedOperationException("unsupported encode message:" + data));

    }

    @Override
    public Flux<? extends EncodedMessage> decode(RuleData data, Feature... features) {
        return Feature.find(TransportFeature.class, features)
                .map(TransportFeature::getTransport)
                .map(Transport::getId)
                .map(transportCodecs::get)
                .map(codec -> codec.decode(data, features))
                .orElseThrow(() -> new UnsupportedOperationException("unsupported decode message:" + data));
    }
}
