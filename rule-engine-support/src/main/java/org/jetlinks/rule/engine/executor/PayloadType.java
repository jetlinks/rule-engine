package org.jetlinks.rule.engine.executor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.jetlinks.rule.engine.api.RuleDataCodec;

import java.nio.charset.StandardCharsets;

public enum PayloadType implements RuleDataCodec.Feature {

    JSON {
        @Override
        public Object read(ByteBuf byteBuf) {
            return com.alibaba.fastjson.JSON.parse(byteBuf.toString(StandardCharsets.UTF_8));
        }

        public ByteBuf write(Object data) {
            if (!(data instanceof String)) {
                data = com.alibaba.fastjson.JSON.toJSONString(data);
            }
            return Unpooled.wrappedBuffer(String.valueOf(data).getBytes());
        }
    },
    STRING {
        @Override
        public String read(ByteBuf byteBuf) {
            return byteBuf.toString(StandardCharsets.UTF_8);
        }

        public ByteBuf write(Object data) {
            if (!(data instanceof String)) {
                data = com.alibaba.fastjson.JSON.toJSONString(data);
            }
            return Unpooled.wrappedBuffer(String.valueOf(data).getBytes());
        }
    },
    BINARY {
        @Override
        public byte[] read(ByteBuf byteBuf) {
            byte[] req = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(req);
            byteBuf.resetReaderIndex();
            return req;
        }

        public ByteBuf write(Object data) {
            if (data instanceof byte[]) {
                return Unpooled.wrappedBuffer((byte[]) data);
            }
            return Unpooled.wrappedBuffer(String.valueOf(data).getBytes());
        }
    },
    HEX {
        @Override
        public String read(ByteBuf byteBuf) {
            return Hex.encodeHexString((byte[]) BINARY.read(byteBuf));
        }

        @SneakyThrows
        public ByteBuf write(Object data) {
            if (data instanceof byte[]) {
                return Unpooled.wrappedBuffer(Hex.decodeHex(new String((byte[]) data)));
            }
            if (data instanceof char[]) {
                return Unpooled.wrappedBuffer(Hex.decodeHex((char[]) data));
            }
            return Unpooled.wrappedBuffer(Hex.decodeHex(String.valueOf(data)));
        }
    };

    public abstract ByteBuf write(Object data);

    public abstract Object read(ByteBuf byteBuf);

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getId() {
        return name();
    }
}
