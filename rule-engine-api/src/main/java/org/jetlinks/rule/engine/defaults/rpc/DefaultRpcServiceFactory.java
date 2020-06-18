package org.jetlinks.rule.engine.defaults.rpc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.jetlinks.core.utils.BytesUtils;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.codec.Codec;
import org.jetlinks.rule.engine.api.codec.Codecs;
import org.jetlinks.rule.engine.api.rpc.RpcDefinition;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.jetlinks.rule.engine.api.rpc.RpcServiceFactory;
import org.jetlinks.rule.engine.defaults.codec.DirectCodec;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

@AllArgsConstructor
public class DefaultRpcServiceFactory implements RpcServiceFactory {

    RpcService rpcService;

    @Override
    @SuppressWarnings("all")
    public <T> T createProducer(String address, Class<T> serviceInterface) {
        if (!serviceInterface.isInterface()) {
            throw new UnsupportedOperationException("only support interface class");
        }

        Map<Method, RpcDefinition<MethodRpcRequest, ?>> defs = new HashMap<>();
        for (Method method : serviceInterface.getMethods()) {
            RpcDefinition<MethodRpcRequest, ?> def = createRpcDefinition(serviceInterface, method);
            defs.put(method, def);
        }

        RpcDefinition<Payload, Payload> rpcDef = RpcDefinition.of(address, DirectCodec.INSTANCE, DirectCodec.INSTANCE);

        InvocationHandler handler = (proxy, method, args) -> {
            RpcDefinition<MethodRpcRequest, ?> definition = defs.get(method);

            Flux<?> flux = rpcService
                    .invoke(rpcDef, definition.requestCodec().encode(new MethodRpcRequest(definition.getAddress(), args)))
                    .flatMap(payload -> Mono.justOrEmpty(definition.responseCodec().decode(payload)));
            if (method.getReturnType().isAssignableFrom(Mono.class)) {
                return Mono.from(flux);
            }
            return flux;
        };

        return (T) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{serviceInterface}, handler);
    }

    @Override
    public <T> Disposable createConsumer(String address, Class<T> serviceInterface, T instance) {

        Map<String, Function<Payload, Publisher<Payload>>> handlers = new HashMap<>();

        for (Method declaredMethod : serviceInterface.getDeclaredMethods()) {
            RpcDefinition<MethodRpcRequest, ?> definition = createRpcDefinition(instance.getClass(), declaredMethod);

            handlers.put(definition.getAddress(), new Function<Payload, Publisher<Payload>>() {
                @Override
                @SneakyThrows
                @SuppressWarnings("all")
                public Publisher<Payload> apply(Payload payload) {
                    MethodRpcRequest request = definition.requestCodec().decode(payload);
                    Object result = declaredMethod.invoke(instance, request.getArgs());
                    Codec codec = definition.responseCodec();
                    if (result instanceof Mono) {
                        return ((Mono<Object>) result)
                                .map(res -> codec.encode(res));
                    } else if (result instanceof Flux) {
                        return ((Flux<Object>) result)
                                .map(res -> codec.encode(res));
                    }
                    return Mono.just(codec.encode(result));
                }
            });

        }

        return rpcService.listen(RpcDefinition.of(address, requestCodec, DirectCodec.INSTANCE), (addr, method) ->
                handlers
                        .get(method.getT1())
                        .apply(method.getT2()));
    }


    private RpcDefinition<MethodRpcRequest, ?> createRpcDefinition(Class<?> type, Method method) {

        int count = method.getParameterCount();
        List<Codec> codecs = new ArrayList<>();
        StringBuilder methodName = new StringBuilder(method.getName());

        for (int i = 0; i < count; i++) {
            ResolvableType resolvableType = ResolvableType.forMethodParameter(method, i);
            Class<?> paramType = resolvableType.resolve(Object.class);
            codecs.add(Codecs.lookup(resolvableType));
            methodName
                    .append(paramType.getSimpleName())
                    .append(",");
        }

        MethodRpcRequestCodec codec = new MethodRpcRequestCodec(methodName.toString().getBytes(), codecs);

        ResolvableType returnType = ResolvableType.forMethodReturnType(method,type);
        if (!Publisher.class.isAssignableFrom(returnType.toClass())) {
            throw new UnsupportedOperationException("unsupported return type:" + returnType);
        }

        return RpcDefinition.of(methodName.toString(), codec, Codecs.lookup(returnType));
    }

    static RpcRequestCodec requestCodec = new RpcRequestCodec();

    static class RpcRequestCodec implements Codec<Tuple2<String, Payload>> {

        @Nullable
        @Override
        public Tuple2<String, Payload> decode(@Nonnull Payload payload) {
            ByteBuf buf = payload.getBody();

            byte[] lenArr = new byte[4];
            buf.readBytes(lenArr);
            int len = BytesUtils.beToInt(lenArr);
            ByteBuf byteBuf = buf.readBytes(len);

            buf.resetReaderIndex();
            return Tuples.of(byteBuf.toString(StandardCharsets.UTF_8), payload);
        }

        @Override
        public Payload encode(Tuple2<String, Payload> tuple2) {
            byte[] bytes = tuple2.getT1().getBytes();
            ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(4 + bytes.length);
            buf.writeBytes(BytesUtils.intToBe(bytes.length));
            buf.writeBytes(bytes);
            return Payload.of(buf);
        }
    }

    /**
     *
     */
    @AllArgsConstructor
    class MethodRpcRequestCodec implements Codec<MethodRpcRequest> {
        private final byte[] method;
        private final List<Codec> parameter;

        @Nullable
        @Override
        public MethodRpcRequest decode(@Nonnull Payload payload) {
            ByteBuf buf = payload.getBody().skipBytes(4 + method.length);
            Object[] args = new Object[parameter.size()];
            int i = 0;
            for (Codec<?> codec : parameter) {
                byte[] lenArr = new byte[4];
                buf.readBytes(lenArr);
                int len = BytesUtils.beToInt(lenArr);
                ByteBuf data = buf.readBytes(len);
                args[i++] = codec.decode(Payload.of(data));
            }
            buf.resetReaderIndex();
            return new MethodRpcRequest(new String(method), args);
        }

        @Override
        public Payload encode(MethodRpcRequest body) {
            ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(4 + method.length);
            buf.writeBytes(BytesUtils.intToBe(method.length));
            buf.writeBytes(method);

            Object[] params = body.getArgs();
            if (params != null) {
                for (int i = 0, size = params.length; i < size; i++) {
                    ByteBuf data = parameter.get(i).encode(params[i]).getBody();
                    buf.writeBytes(BytesUtils.intToBe(data.capacity()));
                    buf.writeBytes(data);
                }
            }

            return Payload.of(buf);
        }
    }

    @AllArgsConstructor
    @Getter
    static final class MethodRpcRequest {
        private final String method;

        private final Object[] args;

    }


}
