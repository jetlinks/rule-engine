package org.jetlinks.rule.engine.executor.node.mqtt.vertx;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.SimpleMqttMessage;
import org.jetlinks.rule.engine.executor.node.mqtt.MqttClient;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class VertxMqttClient implements MqttClient {

    @Getter
    private io.vertx.mqtt.MqttClient client;

    private FluxProcessor<MqttMessage, MqttMessage> messageProcessor;

    private Set<String> topics = new HashSet<>();

    private boolean neverSubscribe = true;

    public void setClient(io.vertx.mqtt.MqttClient client) {
        this.client = client;
        client.publishHandler(msg -> {
            if (neverSubscribe || messageProcessor.hasDownstreams()) {
                messageProcessor
                        .onNext(SimpleMqttMessage
                                .builder()
                                .topic(msg.topicName())
                                .deviceId(client.clientId())
                                .qosLevel(msg.qosLevel().value())
                                .retain(msg.isRetain())
                                .dup(msg.isDup())
                                .payload(msg.payload().getByteBuf())
                                .messageId(msg.messageId())
                                .build());
            }
        });
        if (!topics.isEmpty()) {
            client.subscribe(topics.stream().collect(Collectors.toMap(Function.identity(), (r) -> 0)));
        }
    }

    public VertxMqttClient(io.vertx.mqtt.MqttClient client) {
        messageProcessor = EmitterProcessor.create(false);
        setClient(client);
    }

    @Override
    public Flux<MqttMessage> subscribe(List<String> topics) {
        neverSubscribe = false;
        return Flux.defer(() -> {
            log.info("subscribe mqtt [{}] topic : {}", client.clientId(), topics);
            client.subscribe(topics.stream()
                    .filter(r -> !this.topics.contains(r))
                    .collect(Collectors.toMap(Function.identity(), (r) -> 0)));
            this.topics.addAll(topics);
            return messageProcessor
                    .filter(msg -> {
                        // TODO: 2019-11-05 topic判断
                        return true;
                    });
        }).doFinally(r -> {
            log.info("unsubscribe mqtt [{}] topic : {}", client.clientId(), topics);
            for (String topic : topics) {
                client.unsubscribe(topic);
            }
        });
    }

    @Override
    public Mono<Boolean> publish(MqttMessage message) {
        return Mono.create((sink) -> client.publish(message.getTopic(),
                Buffer.buffer(message.getPayload()),
                MqttQoS.valueOf(message.getQosLevel()),
                message.isDup(),
                message.isRetain(),
                result -> {
                    if (result.succeeded()) {
                        log.info("publish mqtt [{}] message success: {}", client.clientId(), message);
                        sink.success(true);
                    } else {
                        log.info("publish mqtt [{}] message error : {}", client.clientId(), message, result.cause());
                        sink.error(result.cause());
                    }
                }));
    }

    @Override
    public boolean isAlive() {
        return client.isConnected();
    }
}
