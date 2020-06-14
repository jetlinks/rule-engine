package org.jetlinks.rule.engine.defaults.codec;

import io.netty.buffer.Unpooled;
import org.jetlinks.core.utils.BytesUtils;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import javax.annotation.Nonnull;

public class FloatCodec implements Codec<Float> {

    public static FloatCodec INSTANCE = new FloatCodec();

    private FloatCodec() {

    }

    @Override
    public Float decode(@Nonnull Payload payload) {
        return BytesUtils.beToFloat(payload.bodyAsBytes());
    }

    @Override
    public Payload encode(Float body) {
        return () -> Unpooled.wrappedBuffer(BytesUtils.floatToBe(body));
    }


}
