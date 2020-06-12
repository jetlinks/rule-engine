package org.jetlinks.rule.engine.api.codec;

import java.util.List;
import java.util.Optional;

public interface CodecsSupport {

    <T> Optional<Codec<T>> lookup(Class<T> target);

    <T> Optional<Codec<List<T>>> lookupForList(Class<T> target);

    int getOrder();
}
