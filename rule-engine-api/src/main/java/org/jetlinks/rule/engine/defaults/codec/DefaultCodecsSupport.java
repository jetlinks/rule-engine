package org.jetlinks.rule.engine.defaults.codec;

import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.codec.Codec;
import org.jetlinks.rule.engine.api.codec.CodecsSupport;

import java.util.*;

@SuppressWarnings("all")
public class DefaultCodecsSupport implements CodecsSupport {


    private static Map<Class, Codec> staticCodec = new HashMap<>();

    static {

        staticCodec.put(int.class, IntegerCodec.INSTANCE);
        staticCodec.put(Integer.class, IntegerCodec.INSTANCE);

        staticCodec.put(long.class, LongCodec.INSTANCE);
        staticCodec.put(Long.class, LongCodec.INSTANCE);

        staticCodec.put(double.class, DoubleCodec.INSTANCE);
        staticCodec.put(Double.class, DoubleCodec.INSTANCE);

        staticCodec.put(float.class, FloatCodec.INSTANCE);
        staticCodec.put(Float.class, FloatCodec.INSTANCE);

        staticCodec.put(boolean.class, BooleanCodec.INSTANCE);
        staticCodec.put(Boolean.class, BooleanCodec.INSTANCE);

        staticCodec.put(String.class, StringCodec.UTF8);
        staticCodec.put(byte[].class, BytesCodec.INSTANCE);

        {
            JsonCodec<RuleData> codec = JsonCodec.of(RuleData.class);
            staticCodec.put(RuleData.class, codec);
        }

        {
            JsonCodec<Map> codec = JsonCodec.of(Map.class);
            staticCodec.put(Map.class, codec);
            staticCodec.put(HashMap.class, codec);
            staticCodec.put(LinkedHashMap.class, codec);
        }

    }

    @Override
    @SuppressWarnings("all")
    public <T> Optional<Codec<T>> lookup(Class<T> target) {
        Codec codec = staticCodec.get(target);
        if (codec == null) {
            if (target.isEnum()) {
                codec = EnumCodec.of((Enum[]) target.getEnumConstants());
            }
            if (Payload.class.isAssignableFrom(target)) {
                codec = DirectCodec.INSTANCE;
            }
        }
        if (codec == null) {
            codec = JsonCodec.of(target);
        }
        return Optional.of(codec);
    }

    @Override
    public <T> Optional<Codec<List<T>>> lookupForList(Class<T> target) {
        return Optional.of(JsonArrayCodec.of(target));
    }


    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }
}
