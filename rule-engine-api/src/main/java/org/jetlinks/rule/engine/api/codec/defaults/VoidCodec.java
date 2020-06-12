package org.jetlinks.rule.engine.api.codec.defaults;


import io.netty.buffer.Unpooled;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

public class VoidCodec implements Codec<Void> {

    public static VoidCodec INSTANCE = new VoidCodec();

    @Override
    public Void decode(Payload payload) {
        return null;
    }

    @Override
    public Payload encode(Void body) {

        return () -> Unpooled.wrappedBuffer(new byte[0]);
    }

}
