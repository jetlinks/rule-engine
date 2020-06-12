package org.jetlinks.rule.engine.api.codec.defaults;

import org.jetlinks.rule.engine.api.DefaultRuleData;
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

        staticCodec.put(boolean.class, IntegerCodec.INSTANCE);
        staticCodec.put(Boolean.class, IntegerCodec.INSTANCE);

        staticCodec.put(String.class, StringCodec.UTF8);
        staticCodec.put(byte[].class, BytesCodec.INSTANCE);

        {
            JsonCodec<DefaultRuleData> codec = JsonCodec.of(DefaultRuleData.class);
            staticCodec.put(RuleData.class, codec);
            staticCodec.put(DefaultRuleData.class, codec);
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
        }
        return Optional.ofNullable(codec);
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
