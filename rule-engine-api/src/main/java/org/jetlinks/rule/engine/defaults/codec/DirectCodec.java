package org.jetlinks.rule.engine.defaults.codec;

import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import javax.annotation.Nonnull;


public class DirectCodec implements Codec<Payload> {

    public static final DirectCodec INSTANCE = new DirectCodec();

    @Override
    public Payload decode(@Nonnull Payload payload) {
        return payload;
    }

    @Override
    public Payload encode(Payload body) {
        return body;
    }
}
