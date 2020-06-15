package org.jetlinks.rule.engine.api;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

@Getter
@Setter
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
public class NativePayload implements Payload {

    private Object nativeObject;

    private Supplier<ByteBuf> bodySupplier;

    @Nonnull
    @Override
    public ByteBuf getBody() {
        return bodySupplier.get();
    }

    @Override
    public <T> T bodyAs(Class<T> type) {
        if (type.isInstance(nativeObject)) {
            return type.cast(nativeObject);
        }
        return Payload.super.bodyAs(type);
    }
}
