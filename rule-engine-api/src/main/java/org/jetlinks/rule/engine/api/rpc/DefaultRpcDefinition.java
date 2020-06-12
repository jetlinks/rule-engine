package org.jetlinks.rule.engine.api.rpc;


import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.codec.Codec;

@AllArgsConstructor
public class DefaultRpcDefinition<REQ, RES> implements RpcDefinition<REQ, RES> {

    private final String address;

    private final Codec<? extends REQ> requestCodec;
    private final Codec<? extends RES> responseCodec;

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public Codec<? extends REQ> requestCodec() {
        return requestCodec;
    }

    @Override
    public Codec<? extends RES> responseCodec() {
        return responseCodec;
    }

}
