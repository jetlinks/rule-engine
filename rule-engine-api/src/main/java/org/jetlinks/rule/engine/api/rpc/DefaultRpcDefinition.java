package org.jetlinks.rule.engine.api.rpc;


import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.codec.Codec;

@AllArgsConstructor
public class DefaultRpcDefinition<REQ, RES> implements RpcDefinition<REQ, RES> {

    private final String address;

    private final Codec<REQ> requestCodec;
    private final Codec<RES> responseCodec;

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public Codec<REQ> requestCodec() {
        return requestCodec;
    }

    @Override
    public Codec<RES> responseCodec() {
        return responseCodec;
    }

}
