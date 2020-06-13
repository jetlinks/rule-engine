package org.jetlinks.rule.engine.api.codec.defaults;

import io.netty.buffer.Unpooled;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorCodec implements Codec<Throwable> {

    public static ErrorCodec RUNTIME = of(RuntimeException::new);


    public static ErrorCodec DEFAULT = RUNTIME;


    public static ErrorCodec of(Function<String, Throwable> mapping) {
        return new ErrorCodec(mapping);
    }

    Function<String, Throwable> mapping;

    @Override
    public Throwable decode(Payload payload) {
        return mapping.apply(payload.bodyAsString());
    }

    @Override
    public Payload encode(Throwable body) {
        String message = body.getMessage() == null ? body.getClass().getSimpleName() : body.getMessage();
        return () -> Unpooled.wrappedBuffer(message.getBytes());
    }
}
