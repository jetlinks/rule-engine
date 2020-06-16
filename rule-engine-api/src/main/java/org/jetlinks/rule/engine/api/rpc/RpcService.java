package org.jetlinks.rule.engine.api.rpc;

import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Rpc 服务
 *
 * @author zhouhao
 * @since 1.0.4
 */
public interface RpcService {

    /**
     * 监听请求,相当于发布服务.
     *
     * @param definition RPC定义
     * @param call       请求回掉
     * @param <REQ>      请求类型
     * @param <RES>      响应类型
     * @return Disposable
     */
    <REQ, RES> Disposable listen(RpcDefinition<REQ, RES> definition,
                                 BiFunction<String, REQ, Publisher<RES>> call);

    /**
     * 监听没有参数的请求.
     *
     * @param definition RPC定义
     * @param call       请求回掉
     * @param <RES>      响应类型
     * @return Disposable
     */
    <RES> Disposable listen(RpcDefinition<Void, RES> definition,
                            Function<String, Publisher<RES>> call);

    /**
     * 调用RPC服务
     *
     * @param definition RPC定义
     * @param payload    请求参数
     * @param <REQ>      参数类型
     * @param <RES>      响应类型
     * @return 响应结果
     */
    <REQ, RES> Flux<RES> invoke(RpcDefinition<REQ, RES> definition, Publisher<? extends REQ> payload);

    /**
     * 调用没有参数的RPC服务
     *
     * @param definition RPC定义
     * @param <RES>      响应类型
     * @return 响应结果
     */
    <RES> Flux<RES> invoke(RpcDefinition<Void, RES> definition);

    /**
     * 调用RPC服务
     *
     * @param definition RCP定义
     * @param payload    请求体
     * @param <REQ>      请求类型
     * @param <RES>      响应类型
     * @return 响应结果
     */
    default <REQ, RES> Flux<RES> invoke(RpcDefinition<REQ, RES> definition, REQ payload) {
        return invoke(definition, Mono.just(payload));
    }

}
