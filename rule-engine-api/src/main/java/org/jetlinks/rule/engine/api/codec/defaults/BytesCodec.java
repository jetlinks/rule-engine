package org.jetlinks.rule.engine.api.codec.defaults;

import io.netty.buffer.Unpooled;
import org.jetlinks.core.utils.BytesUtils;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

public class BytesCodec implements Codec<byte[]> {

    public static BytesCodec INSTANCE = new BytesCodec();

    private BytesCodec() {

    }

    @Override
    public byte[] decode(Payload payload) {
        return payload.bodyAsBytes();
    }

    @Override
    public Payload encode(byte[] body) {
        return () -> Unpooled.wrappedBuffer(body);
    }


}
