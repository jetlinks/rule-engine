package org.jetlinks.rule.engine.defaults.codec;

import io.netty.buffer.Unpooled;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import javax.annotation.Nonnull;

public class BooleanCodec implements Codec<Boolean> {

    public static BooleanCodec INSTANCE = new BooleanCodec();

    private BooleanCodec() {

    }

    @Override
    public Boolean decode(@Nonnull Payload payload) {
        byte[] data = payload.bodyAsBytes();

        return data.length > 0 && data[0] > 0;
    }

    @Override
    public Payload encode(Boolean body) {
        return () -> Unpooled.wrappedBuffer(new byte[]{body ? (byte) 1 : 0});
    }

}
