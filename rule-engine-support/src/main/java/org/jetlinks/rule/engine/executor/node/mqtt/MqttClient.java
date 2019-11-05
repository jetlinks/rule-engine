package org.jetlinks.rule.engine.executor.node.mqtt;

import org.jetlinks.core.message.codec.MqttMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface MqttClient {

    Flux<MqttMessage> subscribe(List<String> topics);

    Mono<Boolean> publish(MqttMessage message);

    boolean isAlive();

}
