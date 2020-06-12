package org.jetlinks.rule.engine.api.rpc;

import org.jetlinks.rule.engine.api.codec.Codec;
import org.jetlinks.rule.engine.api.codec.Codecs;

/**
 * Rpc定义信息
 *
 * @param <REQ> 请求类型
 * @param <RES> 响应类型
 */
public interface RpcDefinition<REQ, RES> {

    /**
     * 服务地址
     *
     * @return 地址
     */
    String getAddress();

    /**
     * @return 请求编解码器
     */
    Codec<? extends REQ> requestCodec();

    /**
     * @return 响应编解码器
     */
    Codec<? extends RES> responseCodec();

    static <REQ, RES> RpcDefinition<REQ, RES> of(String address,
                                                 Codec<? extends REQ> requestCodec,
                                                 Codec<? extends RES> responseCodec) {
        return new DefaultRpcDefinition<>(address, requestCodec, responseCodec);
    }

    static RpcDefinition<Void, Void> of(String address) {
        return new DefaultRpcDefinition<>(address, Codecs.lookup(Void.class), Codecs.lookup(Void.class));
    }

    static <REQ, RES> RpcDefinition<REQ, RES> of(String address,
                                                 Class<REQ> requestType,
                                                 Class<RES> responseType) {
        return new DefaultRpcDefinition<>(address, Codecs.lookup(requestType), Codecs.lookup(responseType));
    }

    static <RES> RpcDefinition<Void, RES> ofNoParameter(String address, Class<RES> responseType) {
        return new DefaultRpcDefinition<>(address, Codecs.lookup(Void.class), Codecs.lookup(responseType));
    }

    static <REQ> RpcDefinition<REQ, Void> ofNoResponse(String address, Class<REQ> requestType) {
        return new DefaultRpcDefinition<>(address, Codecs.lookup(requestType), Codecs.lookup(Void.class));
    }
}
