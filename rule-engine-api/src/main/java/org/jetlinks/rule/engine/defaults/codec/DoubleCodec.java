package org.jetlinks.rule.engine.defaults.codec;

import io.netty.buffer.Unpooled;
import org.jetlinks.core.utils.BytesUtils;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import javax.annotation.Nonnull;

public class DoubleCodec implements Codec<Double> {

    public static DoubleCodec INSTANCE = new DoubleCodec();

    private DoubleCodec() {

    }

    @Override
    public Double decode(@Nonnull Payload payload) {
        return BytesUtils.beToDouble(payload.bodyAsBytes());
    }

    @Override
    public Payload encode(Double body) {
        return () -> Unpooled.wrappedBuffer(BytesUtils.doubleToBe(body));
    }


}
