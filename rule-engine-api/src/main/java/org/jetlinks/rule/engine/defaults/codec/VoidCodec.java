package org.jetlinks.rule.engine.defaults.codec;


import io.netty.buffer.Unpooled;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import javax.annotation.Nonnull;

public class VoidCodec implements Codec<Void> {

    public static VoidCodec INSTANCE = new VoidCodec();

    @Override
    public Void decode(@Nonnull Payload payload) {
        return null;
    }

    @Override
    public Payload encode(Void body) {

        return () -> Unpooled.wrappedBuffer(new byte[0]);
    }

}
