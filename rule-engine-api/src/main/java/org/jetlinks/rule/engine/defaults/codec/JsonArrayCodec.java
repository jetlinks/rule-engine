package org.jetlinks.rule.engine.defaults.codec;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.Unpooled;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;

public class JsonArrayCodec<T, R> implements Codec<R> {

    private final Class<T> type;

    private final Function<List<T>, R> mapper;

    private JsonArrayCodec(Class<T> type, Function<List<T>, R> mapper) {
        this.type = type;
        this.mapper = mapper;
    }

    public static <T> JsonArrayCodec<T, List<T>> of(Class<T> type) {
        return of(type, Function.identity());
    }

    public static <T, R> JsonArrayCodec<T, R> of(Class<T> type, Function<List<T>, R> function) {
        return new JsonArrayCodec<>(type, function);
    }

    @Override
    public R decode(@Nonnull Payload payload) {
        return mapper.apply(JSON.parseArray(payload.bodyAsString(), type));
    }

    @Override
    public Payload encode(R body) {
        return () -> Unpooled.wrappedBuffer(JSON.toJSONBytes(body));
    }


}
