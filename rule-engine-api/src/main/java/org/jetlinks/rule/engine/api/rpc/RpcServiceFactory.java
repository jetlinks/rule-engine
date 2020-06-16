package org.jetlinks.rule.engine.api.rpc;

import reactor.core.Disposable;

public interface RpcServiceFactory {

    <T> T createProducer(String address, Class<T> serviceInterface);

    <T> Disposable createConsumer(String address, Class<T> serviceInterface, T instance);

}
