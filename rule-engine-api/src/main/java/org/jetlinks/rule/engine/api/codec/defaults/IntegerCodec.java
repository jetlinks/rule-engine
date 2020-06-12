package org.jetlinks.rule.engine.api.codec.defaults;

import io.netty.buffer.Unpooled;
import org.jetlinks.core.utils.BytesUtils;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

public class IntegerCodec implements Codec<Integer> {

    public static IntegerCodec INSTANCE = new IntegerCodec();

    private IntegerCodec() {

    }

    @Override
    public Integer decode(Payload payload) {
        return BytesUtils.beToInt(payload.bodyAsBytes());
    }

    @Override
    public Payload encode(Integer body) {
        return () -> Unpooled.wrappedBuffer(BytesUtils.intToBe(body));
    }


}
