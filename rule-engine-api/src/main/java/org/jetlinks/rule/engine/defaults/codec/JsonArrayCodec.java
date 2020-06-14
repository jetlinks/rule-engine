package org.jetlinks.rule.engine.defaults.codec;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.Unpooled;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import javax.annotation.Nonnull;
import java.util.List;

public class JsonArrayCodec<T> implements Codec<List<T>> {

    private final Class<T> type;

    private JsonArrayCodec(Class<T> type) {
        this.type = type;
    }

    public static <T> JsonArrayCodec<T> of(Class<T> type) {
        return new JsonArrayCodec<>(type);
    }

    @Override
    public List<T> decode(@Nonnull Payload payload) {
        return JSON.parseArray(payload.bodyAsString(), type);
    }

    @Override
    public Payload encode(List<T> body) {
        return () -> Unpooled.wrappedBuffer(JSON.toJSONBytes(body));
    }


}
