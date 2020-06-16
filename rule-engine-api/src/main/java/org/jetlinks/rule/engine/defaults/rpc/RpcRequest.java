package org.jetlinks.rule.engine.defaults.rpc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import org.jetlinks.core.utils.BytesUtils;
import org.jetlinks.rule.engine.api.Payload;

public class RpcRequest implements Payload {

    private static final Type[] types = Type.values();

    @Getter
    private final Type type;

    @Getter
    private final long requestId;

    @Getter
    private final ByteBuf body;

    public static RpcRequest parse(Payload payload) {
        return new RpcRequest(payload);
    }

    public static RpcRequest next(long requestId, Payload payload) {
        return new RpcRequest(Type.NEXT, requestId, payload);
    }

    public static RpcRequest complete(long requestId) {
        return new RpcRequest(Type.COMPLETE, requestId, voidPayload);
    }

    public static RpcRequest nextAndComplete(long requestId, Payload payload) {
        return new RpcRequest(Type.NEXT_AND_END, requestId, payload);
    }

    private RpcRequest(Type type, long requestId, Payload payload) {
        ByteBuf byteBuf = Unpooled.buffer(9 + payload.getBody().capacity());

        byteBuf.writeByte(type.ordinal());
        byteBuf.writeBytes(BytesUtils.longToBe(requestId));
        byteBuf.writeBytes(payload.getBody());

        this.type = type;
        this.body = byteBuf;
        this.requestId = requestId;
    }

    private RpcRequest(Payload payload) {
        ByteBuf byteBuf = payload.getBody();
        this.type = types[byteBuf.readByte()];
        byte[] msgId = new byte[8];
        byteBuf.getBytes(1, msgId);
        this.requestId = BytesUtils.beToLong(msgId);
        this.body = byteBuf.slice(9, byteBuf.capacity() - 9);
        byteBuf.resetReaderIndex();
    }

    public enum Type {
        NEXT,
        COMPLETE,
        NEXT_AND_END
    }
}
