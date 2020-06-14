package org.jetlinks.rule.engine.defaults.codec;

import io.netty.buffer.Unpooled;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import javax.annotation.Nonnull;

public class BytesCodec implements Codec<byte[]> {

    public static BytesCodec INSTANCE = new BytesCodec();

    private BytesCodec() {

    }

    @Override
    public byte[] decode(@Nonnull Payload payload) {
        return payload.bodyAsBytes();
    }

    @Override
    public Payload encode(byte[] body) {
        return () -> Unpooled.wrappedBuffer(body);
    }


}
