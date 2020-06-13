package org.jetlinks.rule.engine.api;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

public interface Payload {

    @Nonnull
    ByteBuf getBody();

    default byte[] bodyAsBytes() {
        return ByteBufUtil.getBytes(getBody());
    }

    default byte[] bodyAsBytes(int offset, int length) {
        return ByteBufUtil.getBytes(getBody(), offset, length);
    }

    default String bodyAsString() {
        return getBody().toString(StandardCharsets.UTF_8);
    }

    Payload voidPayload = () -> Unpooled.wrappedBuffer(new byte[0]);
}
