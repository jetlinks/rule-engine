package org.jetlinks.rule.engine.api.codec.defaults;

import io.netty.buffer.Unpooled;
import org.jetlinks.core.utils.BytesUtils;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

public class LongCodec implements Codec<Long> {

    public static LongCodec INSTANCE = new LongCodec();

    private LongCodec() {

    }

    @Override
    public Long decode(Payload payload) {
        return BytesUtils.beToLong(payload.bodyAsBytes());
    }

    @Override
    public Payload encode(Long body) {
        return () -> Unpooled.wrappedBuffer(BytesUtils.longToBe(body));
    }


}
