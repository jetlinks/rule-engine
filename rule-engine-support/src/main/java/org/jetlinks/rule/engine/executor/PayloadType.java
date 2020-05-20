package org.jetlinks.rule.engine.executor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import lombok.SneakyThrows;
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

            return ByteBufUtil.getBytes(byteBuf);
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
            return ByteBufUtil.hexDump((byte[]) BINARY.read(byteBuf));
        }

        @SneakyThrows
        public ByteBuf write(Object data) {
            String hex;
            if (data instanceof byte[]) {
                hex = new String((byte[]) data);
            } else if (data instanceof char[]) {
                hex = new String(((char[]) data));
            } else if (data instanceof ByteBuf) {
                hex = new String(ByteBufUtil.getBytes(((ByteBuf) data)));
            } else {
                hex = String.valueOf(data);
            }
            return Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(hex.replace("\n", "").replace(" ", "")));
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
