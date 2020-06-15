package org.jetlinks.rule.engine.api.codec;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("all")
@Slf4j
public final class Codecs {

    private static Map<Cache, Codec<?>> mapping = new ConcurrentHashMap<>();

    private static List<CodecsSupport> allCodec = new CopyOnWriteArrayList<>();

    static {
        ServiceLoader
                .load(CodecsSupport.class)
                .forEach(allCodec::add);

        allCodec.sort(Comparator.comparingInt(CodecsSupport::getOrder));
    }

    public static final void register(CodecsSupport support) {
        allCodec.add(support);
        allCodec.sort(Comparator.comparingInt(CodecsSupport::getOrder));
    }

    @Nonnull
    private static Codec<?> resolve(Cache target) {
        for (CodecsSupport support : allCodec) {
            Optional<Codec<?>> lookup;
            if (target.getType() == Type.ARR) {
                lookup = support.lookupForList(target.clazz);
            } else {
                lookup = support.lookup(target.clazz);
            }
            if (lookup.isPresent()) {
                log.debug("lookup codec [{}] for [{}]",lookup.get(),target.getClazz());
                return lookup.get();
            }
        }
        throw new UnsupportedOperationException("unsupported codec for " + target);
    }

    public static <T> Codec<T> lookup(@Nonnull Class<? extends T> target) {
        return (Codec<T>) mapping.computeIfAbsent(new Cache(Type.OBJ, target), t -> resolve(t));
    }

    public static <T> Codec<List<T>> lookupForList(@Nonnull Class<? extends T> target) {
        return (Codec<List<T>>) mapping.computeIfAbsent(new Cache(Type.ARR, target), t -> resolve(t));
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    private static class Cache {
        private Type type;
        private Class clazz;
    }

    private enum Type {
        OBJ, ARR
    }

}
