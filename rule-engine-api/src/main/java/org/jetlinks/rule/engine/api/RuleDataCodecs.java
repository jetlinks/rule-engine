package org.jetlinks.rule.engine.api;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class RuleDataCodecs {

    private static final List<RuleDataCodecSupplier> suppliers = new CopyOnWriteArrayList<>();

    private static final Map<Class, RuleDataCodec<?>> codecs = new ConcurrentHashMap<>();

    public static void register(RuleDataCodecSupplier supplier) {
        suppliers.add(supplier);

        suppliers.sort(Comparator.comparing(RuleDataCodecSupplier::getOrder));
    }

    public static <T> void register(Class<T> type, RuleDataCodec<T> codec) {
        codecs.put(type, codec);
    }

    @SuppressWarnings("all")
    public static <T> Optional<RuleDataCodec<T>> getCodec(Class type) {

        RuleDataCodec codec = (RuleDataCodec) codecs.get(type);
        if (null != codec) {
            return Optional.of(codec);
        }

        synchronized (type) {
            for (RuleDataCodecSupplier supplier : suppliers) {
                if (supplier.isSupport(type)) {
                    codecs.put(type, codec = supplier.getCodec());
                    return Optional.of(codec);
                }
            }
        }

        return Optional.empty();
    }

}
