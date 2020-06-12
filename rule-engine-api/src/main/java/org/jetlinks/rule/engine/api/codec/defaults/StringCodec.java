package org.jetlinks.rule.engine.api.codec.defaults;

import io.netty.buffer.Unpooled;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringCodec implements Codec<String> {

    public static StringCodec DEFAULT = of(Charset.defaultCharset());

    public static StringCodec UTF8 = of(StandardCharsets.UTF_8);

    public static StringCodec ASCII = of(StandardCharsets.US_ASCII);

    public static StringCodec of(Charset charset) {
        return new StringCodec(charset);
    }

    private final Charset charset;

    private StringCodec(Charset charset) {
        this.charset = charset;
    }

    @Override
    public String decode(Payload payload) {

        return payload.getBody().toString(charset);
    }

    @Override
    public Payload encode(String body) {
        return () -> Unpooled.wrappedBuffer(body.getBytes(charset));
    }


}
