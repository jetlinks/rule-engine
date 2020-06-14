package org.jetlinks.rule.engine.defaults.codec;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.Unpooled;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import javax.annotation.Nonnull;

public class JsonCodec<T> implements Codec<T> {

    private final Class<? extends T> type;

    private JsonCodec(Class<? extends T> type) {
        this.type = type;
    }

    public static <T> JsonCodec<T> of(Class<? extends T> type) {
        return new JsonCodec<>(type);
    }

    @Override
    public T decode(@Nonnull Payload payload) {
        return JSON.parseObject(payload.bodyAsBytes(), type);
    }

    @Override
    public Payload encode(T body) {
        return () -> Unpooled.wrappedBuffer(JSON.toJSONBytes(body));
    }

}
