package org.jetlinks.rule.engine.defaults.codec;

import io.netty.buffer.Unpooled;
import org.jetlinks.core.utils.BytesUtils;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import javax.annotation.Nonnull;

public class LongCodec implements Codec<Long> {

    public static LongCodec INSTANCE = new LongCodec();

    private LongCodec() {

    }

    @Override
    public Long decode(@Nonnull Payload payload) {
        return BytesUtils.beToLong(payload.bodyAsBytes());
    }

    @Override
    public Payload encode(Long body) {
        return () -> Unpooled.wrappedBuffer(BytesUtils.longToBe(body));
    }


}
