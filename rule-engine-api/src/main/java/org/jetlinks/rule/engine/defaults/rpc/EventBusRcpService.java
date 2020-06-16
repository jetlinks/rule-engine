package org.jetlinks.rule.engine.defaults.rpc;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.rule.engine.api.EventBus;
import org.jetlinks.rule.engine.api.Payload;
import org.jetlinks.rule.engine.api.rpc.RpcDefinition;
import org.jetlinks.rule.engine.api.rpc.RpcService;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

@AllArgsConstructor
@Slf4j
public class EventBusRcpService implements RpcService {

    private final EventBus eventBus;

    @Override
    public <REQ, RES> Disposable listen(RpcDefinition<REQ, RES> definition, BiFunction<String, REQ, Publisher<RES>> call) {

        return doListen(definition, (s, reqPublisher) -> Flux.from(reqPublisher).flatMap(req -> call.apply(s, req)));
    }

    @Override
    public <RES> Disposable listen(RpcDefinition<Void, RES> definition, Function<String, Publisher<RES>> call) {
        return doListen(definition, (topic, request) -> Flux.from(request).thenMany(call.apply(topic)));
    }

    @Override
    public <REQ, RES> Flux<RES> invoke(RpcDefinition<REQ, RES> definition, Publisher<? extends REQ> payload) {
        return doInvoke(definition, (id, reqTopic) -> {
            if (payload instanceof Mono) {
                return Mono.from(payload)
                        .flatMap(req -> eventBus.publish(reqTopic, RpcRequest.nextAndComplete(id, definition.requestCodec().encode(req))));
            } else {
                return Flux.from(payload)
                        .map(req -> RpcRequest.next(id, definition.requestCodec().encode(req)))
                        .as(req -> eventBus.publish(reqTopic, req))
                        .doOnSuccess((v) -> eventBus.publish(reqTopic, RpcRequest.complete(id)).subscribe());
            }
        });
    }

    @Override
    public <RES> Flux<RES> invoke(RpcDefinition<Void, RES> definition) {
        return doInvoke(definition, (id, reqTopic) -> eventBus.publish(reqTopic, RpcRequest.nextAndComplete(id, Payload.voidPayload)));
    }

    protected Mono<Void> reply(String topic, RcpResult result) {
        return eventBus
                .publish(topic, result)
                .then();
    }

    private class RunningRequest<REQ, RES> {
        long requestId;
        String reqTopicRes;
        String reqTopic;
        RpcDefinition<REQ, RES> definition;
        BiFunction<String, Publisher<REQ>, Publisher<RES>> invoker;
        Disposable disposable;
        EmitterProcessor<REQ> processor = EmitterProcessor.create();
        FluxSink<REQ> sink = processor.sink(FluxSink.OverflowStrategy.BUFFER);

        public RunningRequest(long requestId,
                              RpcDefinition<REQ, RES> definition,
                              BiFunction<String, Publisher<REQ>, Publisher<RES>> invoker,
                              Disposable disposable) {
            this.requestId = requestId;
            this.reqTopic = definition.getAddress();
            this.reqTopicRes = definition.getAddress() + "/_reply";
            this.definition = definition;
            this.invoker = invoker;
            this.disposable = disposable;
            Flux.from(invoker.apply(reqTopic, processor.onBackpressureBuffer()))
                    .flatMap(res -> reply(reqTopicRes, RcpResult.result(requestId, definition.responseCodec().encode(res))))
                    .doOnComplete(() -> reply(reqTopicRes, RcpResult.complete(requestId)).subscribe())
                    .doOnError((e) -> {
                        log.error(e.getMessage(),e);
                        reply(reqTopicRes, RcpResult.error(requestId, definition.errorCodec().encode(e))).subscribe();
                    })
                    .subscribe();
            sink.onDispose(disposable);
        }

        void next(RpcRequest req) {
            try {
                if (req.getType() == RpcRequest.Type.COMPLETE) {
                    sink.complete();
                    return;
                }
                REQ v = definition.requestCodec().decode(req);
                if (v != null) {
                    sink.next(v);
                }
                if (req.getType() == RpcRequest.Type.NEXT_AND_END) {
                    sink.complete();
                }
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
                sink.error(e);
            }
        }

    }

    private <REQ, RES> Disposable doListen(RpcDefinition<REQ, RES> definition,
                                           BiFunction<String, Publisher<REQ>, Publisher<RES>> invokeResult) {

        Map<Long, RunningRequest<REQ, RES>> request = new ConcurrentHashMap<>();

        return eventBus
                .subscribe(definition.getAddress())
                .map(RpcRequest::parse)
                .doOnCancel(request::clear)
                .subscribe(_req -> request.computeIfAbsent(_req.getRequestId(),
                        id -> new RunningRequest<>(id, definition, invokeResult, () -> request.remove(id)))
                        .next(_req)
                );
    }

    private <REQ, RES> Flux<RES> doInvoke(RpcDefinition<REQ, RES> definition, BiFunction<Long, String, Mono<Integer>> doSend) {
        String reqTopic = definition.getAddress();
        String reqTopicRes = definition.getAddress() + "/_reply";
        long id = IDGenerator.SNOW_FLAKE.generate();

        return Flux.create(sink -> {
                    sink.onDispose(eventBus
                            .subscribe(reqTopicRes)
                            .map(RcpResult::parse)
                            .filter(res -> res.getRequestId() == id)
                            .doOnNext(res -> {
                                if (res.getType() == RcpResult.Type.RESULT_AND_COMPLETE) {
                                    RES r = definition.responseCodec().decode(res);
                                    if (r != null) {
                                        sink.next(r);
                                    }
                                    sink.complete();
                                } else if (res.getType() == RcpResult.Type.RESULT) {
                                    RES r = definition.responseCodec().decode(res);
                                    if (r != null) {
                                        sink.next(r);
                                    }
                                } else if (res.getType() == RcpResult.Type.COMPLETE) {
                                    sink.complete();
                                } else if (res.getType() == RcpResult.Type.ERROR) {
                                    Throwable e = definition.errorCodec().decode(res);
                                    if (e != null) {
                                        sink.error(e);
                                    } else {
                                        sink.complete();
                                    }
                                }
                            })
                            .doOnError(sink::error)
                            .subscribe());
                    log.debug("do invoke rpc:{}", definition.getAddress());
                    doSend.apply(id, reqTopic)
                            .map(i -> {
                                if (i == 0) {
                                    throw new UnsupportedOperationException("no rpc service for:" + definition.getAddress());
                                }
                                return i;
                            })
                            .doOnError(sink::error)
                            .subscribe();
                }
        );
    }

}
