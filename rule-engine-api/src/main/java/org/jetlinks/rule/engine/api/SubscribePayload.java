package org.jetlinks.rule.engine.api;

import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnull;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class SubscribePayload implements Payload {

    private String topic;

    private Payload payload;

    @Nonnull
    @Override
    public ByteBuf getBody() {
        return payload.getBody();
    }
}
