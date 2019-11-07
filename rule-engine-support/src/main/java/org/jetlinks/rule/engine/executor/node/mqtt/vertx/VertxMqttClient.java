package org.jetlinks.rule.engine.executor.node.mqtt.vertx;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.message.codec.MqttMessage;
import org.jetlinks.core.message.codec.SimpleMqttMessage;
import org.jetlinks.rule.engine.executor.node.mqtt.MqttClient;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class VertxMqttClient implements MqttClient {

    @Getter
    private io.vertx.mqtt.MqttClient client;

    private FluxProcessor<MqttMessage, MqttMessage> messageProcessor;

    private Map<String, AtomicInteger> topicsSubscribeCounter = new ConcurrentHashMap<>();

    private boolean neverSubscribe = true;

    volatile boolean connecting = false;

    @Getter
    @Setter
    private volatile Throwable lastError;

    public void setClient(io.vertx.mqtt.MqttClient client) {
        this.client = client;
        if (isAlive()) {
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
            if (!topicsSubscribeCounter.isEmpty()) {
                Map<String, Integer> reSubscribe = topicsSubscribeCounter
                        .entrySet()
                        .stream()
                        .filter(e -> e.getValue().get() > 0)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toMap(Function.identity(), (r) -> 0));
                if (!reSubscribe.isEmpty()) {
                    log.info("re subscribe [{}] topic {}", client.clientId(), reSubscribe.keySet());
                    client.subscribe(reSubscribe);
                }
            }
        }

    }

    private AtomicInteger getTopicCounter(String topic) {
        return topicsSubscribeCounter.computeIfAbsent(topic, (ignore) -> new AtomicInteger());
    }

    public VertxMqttClient() {
        messageProcessor = EmitterProcessor.create(false);

    }

    @Override
    public Flux<MqttMessage> subscribe(List<String> topics) {
        neverSubscribe = false;
        return Flux.defer(() -> {
            if (isAlive()) {
                Map<String, Integer> subscribeTopic = topics.stream()
                        .filter(r -> getTopicCounter(r).getAndIncrement() == 0)
                        .collect(Collectors.toMap(Function.identity(), (r) -> 0));
                if (!subscribeTopic.isEmpty()) {
                    log.info("subscribe mqtt [{}] topic : {}", client.clientId(), subscribeTopic);
                    client.subscribe(subscribeTopic);
                }
            }
            return messageProcessor
                    .filter(msg -> {
                        // TODO: 2019-11-05 topic 匹配
                        return true;
                    });
        }).doFinally(r -> {
            if (isAlive()) {
                for (String topic : topics) {
                    if (getTopicCounter(topic).decrementAndGet() <= 0) {
                        log.info("unsubscribe mqtt [{}] topic : {}", client.clientId(), topic);
                        client.unsubscribe(topic);
                    }
                }
            }
        });
    }

    @Override
    public Mono<Boolean> publish(MqttMessage message) {
        return Mono.create((sink) -> {
            if (!isAlive()) {
                sink.error(new IOException("mqtt client not alive"));
                return;
            }
            client.publish(message.getTopic(),
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
                    });
        });
    }

    @Override
    public boolean isAlive() {
        return client != null && client.isConnected();
    }
}
