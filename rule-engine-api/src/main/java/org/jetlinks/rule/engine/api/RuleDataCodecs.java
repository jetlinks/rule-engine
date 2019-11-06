package org.jetlinks.rule.engine.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RuleDataCodecs {

    private static final Map<Class, RuleDataCodec<?>> codecs = new ConcurrentHashMap<>();

    public static <T> void register(Class<T> type,RuleDataCodec<T> codec){
        codecs.put(type,codec);
    }

    @SuppressWarnings("all")
    public static <T,R> Optional<RuleDataCodec<R>> getCodec(Class<T> type) {
        return Optional.ofNullable((RuleDataCodec)codecs.get(type));
    }

}
