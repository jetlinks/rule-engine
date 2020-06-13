package org.jetlinks.rule.engine.cluster.rpc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import org.jetlinks.core.utils.BytesUtils;
import org.jetlinks.rule.engine.api.Payload;

import javax.annotation.Nonnull;

/**
 * rcp result
 */
public class RcpResult implements Payload {

    private final static Type[] types = Type.values();

    @Getter
    private final Type type;

    @Getter
    private final long requestId;

    public static RcpResult parse(Payload payload) {
        return new RcpResult(payload);
    }

    public static RcpResult complete(long messageId) {
        return new RcpResult(messageId, Type.COMPLETE, Payload.voidPayload);
    }

    public static RcpResult complete(long messageId, Payload payload) {
        return new RcpResult(messageId, Type.RESULT_AND_COMPLETE, payload);
    }

    public static RcpResult result(long messageId, Payload payload) {
        return new RcpResult(messageId, Type.RESULT, payload);
    }

    public static RcpResult error(long messageId, Payload payload) {

        return new RcpResult(messageId, Type.ERROR, payload);
    }

    private RcpResult(Payload source) {
        ByteBuf body = source.getBody();
        this.type = types[body.readByte()];
        byte[] msgId = new byte[8];
        body.getBytes(1, msgId);
        this.requestId = BytesUtils.beToLong(msgId);
        this.body = body.slice(9, body.capacity() - 9);
        body.resetReaderIndex();
    }

    private RcpResult(long requestId, Type type, Payload payload) {
        this.type = type;
        ByteBuf byteBuf = Unpooled.buffer(payload.getBody().capacity() + 9);
        byteBuf.writeByte(this.type.ordinal());
        byteBuf.writeBytes(BytesUtils.longToBe(requestId));
        byteBuf.writeBytes(payload.getBody());
        this.body = byteBuf;
        this.requestId = requestId;
    }

    private final ByteBuf body;

    @Nonnull
    @Override
    public ByteBuf getBody() {
        return body;
    }

    public enum Type {
        RESULT,
        COMPLETE,
        RESULT_AND_COMPLETE,
        ERROR
    }
}
