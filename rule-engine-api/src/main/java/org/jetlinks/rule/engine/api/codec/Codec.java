package org.jetlinks.rule.engine.api.codec;

import org.jetlinks.rule.engine.api.Decoder;
import org.jetlinks.rule.engine.api.Encoder;
import org.jetlinks.rule.engine.defaults.codec.VoidCodec;

public interface Codec<T> extends Decoder<T>, Encoder<T> {

    static Codec<Void> voidCodec() {
        return VoidCodec.INSTANCE;
    }

}
