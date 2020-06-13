package org.jetlinks.rule.engine.api.codec.defaults;

import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;


public class DirectCodec implements Codec<Payload> {

    public static final DirectCodec INSTANCE = new DirectCodec();

    @Override
    public Payload decode(Payload payload) {
        return payload;
    }

    @Override
    public Payload encode(Payload body) {
        return body;
    }
}
