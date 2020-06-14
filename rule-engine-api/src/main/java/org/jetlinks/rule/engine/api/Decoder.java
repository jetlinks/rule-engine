package org.jetlinks.rule.engine.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface Decoder<T> {

    @Nullable
    T decode(@Nonnull Payload payload);

}
